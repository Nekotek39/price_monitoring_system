package pl.pricemonitor.api.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import pl.pricemonitor.api.jobs.FetchJobScheduler;
import pl.pricemonitor.api.notification.NotificationService;
import pl.pricemonitor.core.pricing.CompetitorPrice;
import pl.pricemonitor.core.pricing.PricingDecision;
import pl.pricemonitor.core.pricing.PricingEngine;
import pl.pricemonitor.core.pricing.PricingInput;
import pl.pricemonitor.core.pricing.PricingMode;
import pl.pricemonitor.persistence.entity.DecisionStatus;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.PriceRecord;
import pl.pricemonitor.persistence.entity.PricingDecisionRecord;
import pl.pricemonitor.persistence.entity.PricingRule;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.entity.ShopType;
import pl.pricemonitor.persistence.repository.OfferRepository;
import pl.pricemonitor.persistence.repository.PriceRecordRepository;
import pl.pricemonitor.persistence.repository.PricingDecisionRepository;
import pl.pricemonitor.persistence.repository.PricingRuleRepository;
import pl.pricemonitor.persistence.repository.ProductMatchRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dynamic pricing module: on a competitor price change evaluates the rules of the product with the
 * {@link PricingEngine} and executes the decision (price change via the shop API and/or e-mail).
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final PricingRuleRepository rules;
    private final PricingDecisionRepository decisions;
    private final OfferRepository offers;
    private final PriceRecordRepository prices;
    private final ProductMatchRepository matches;
    private final ShopRepository shops;
    private final PricingEngine engine;
    private final SellerShopClient sellerShop;
    private final NotificationService notifications;
    private final FetchJobScheduler jobScheduler;
    private final SystemSettings settings;
    private final Clock clock;

    public PricingService(PricingRuleRepository rules, PricingDecisionRepository decisions, OfferRepository offers,
                          PriceRecordRepository prices, ProductMatchRepository matches, ShopRepository shops,
                          PricingEngine engine, SellerShopClient sellerShop, NotificationService notifications,
                          FetchJobScheduler jobScheduler, SystemSettings settings, Clock clock) {
        this.rules = rules;
        this.decisions = decisions;
        this.offers = offers;
        this.prices = prices;
        this.matches = matches;
        this.shops = shops;
        this.engine = engine;
        this.sellerShop = sellerShop;
        this.notifications = notifications;
        this.jobScheduler = jobScheduler;
        this.settings = settings;
        this.clock = clock;
    }

    /** Trigger: prices of these products changed in a competitor shop. */
    @Transactional
    public void onPricesChanged(Collection<Long> productIds) {
        for (Long productId : productIds) {
            for (PricingRule rule : rules.findByProduct_IdAndActiveTrue(productId)) {
                try {
                    evaluate(rule);
                } catch (RuntimeException e) {
                    log.error("Pricing rule {} failed", rule.getId(), e);
                }
            }
        }
    }

    /** Manual evaluation from the seller panel; returns the engine decision. */
    @Transactional
    public PricingDecision evaluate(PricingRule rule) {
        Instant now = clock.instant();
        String product = rule.getProduct().getName();
        Optional<Shop> sellerShopOpt = shops.findByOwner_IdAndType(rule.getOwner().getId(), ShopType.SELLER).stream()
                .findFirst();
        if (sellerShopOpt.isEmpty()) {
            throw new IllegalStateException("Sprzedawca nie ma podłączonego sklepu");
        }
        Shop sellerShopEntity = sellerShopOpt.get();
        BigDecimal current;
        try {
            current = sellerShop.currentPrice(sellerShopEntity, rule.getShopProductId());
        } catch (RestClientException e) {
            notifications.adminAlert("Błąd API sklepu sprzedawcy", "Nie można odczytać ceny produktu „" + product
                    + "” (" + rule.getShopProductId() + "): " + e.getMessage());
            throw e;
        }

        List<CompetitorPrice> competitorPrices = competitorPrices(rule);
        Optional<Instant> lastChange = decisions
                .findFirstByRule_IdAndStatusOrderByCreatedAtDesc(rule.getId(), DecisionStatus.APPLIED)
                .map(PricingDecisionRecord::getCreatedAt);
        PricingDecision decision = engine.decide(new PricingInput(current, competitorPrices,
                rule.toSettings(settings.maxDataAge()), now, lastChange.orElse(null)));
        log.info("Rule {} ({}): {} - {}", rule.getId(), product, decision.outcome(), decision.reason());

        String sellerEmail = rule.getOwner().getEmail();
        switch (decision.outcome()) {
            case STALE_DATA -> {
                List<Shop> stale = shops.findAllById(decision.staleShopIds());
                jobScheduler.requestOnDemand(rule.getProduct(), stale);
            }
            case KEEP_BELOW_MINIMUM -> {
                if (!isRepeated(rule, DecisionStatus.KEPT, decision.lowestCompetitorPrice())) {
                    save(rule, decision, decision.currentPrice(), DecisionStatus.KEPT, now);
                    notifications.competitorBelowMinimum(sellerEmail, product, decision.lowestCompetitorPrice(),
                            rule.getMinPrice(), decision.reason());
                }
            }
            case CHANGE -> execute(rule, decision, sellerShopEntity, sellerEmail, product, now);
            case NO_CHANGE, TOO_SOON, NO_DATA -> {
                // nothing to do; the outcome is logged above
            }
        }
        return decision;
    }

    private void execute(PricingRule rule, PricingDecision decision, Shop sellerShopEntity, String sellerEmail,
                         String product, Instant now) {
        if (rule.getMode() == PricingMode.AUTOMATIC) {
            try {
                sellerShop.updatePrice(sellerShopEntity, rule.getShopProductId(), decision.finalPrice());
            } catch (RestClientException e) {
                save(rule, decision, decision.finalPrice(), DecisionStatus.FAILED, now,
                        decision.reason() + " Błąd zmiany ceny w sklepie: " + e.getMessage());
                notifications.adminAlert("Błąd zmiany ceny", "Produkt „" + product + "”: " + e.getMessage());
                return;
            }
            save(rule, decision, decision.finalPrice(), DecisionStatus.APPLIED, now);
            notifications.priceChanged(sellerEmail, product, decision.currentPrice(), decision.finalPrice(),
                    decision.reason());
        } else if (!isRepeated(rule, DecisionStatus.SENT, decision.lowestCompetitorPrice())) {
            save(rule, decision, decision.finalPrice(), DecisionStatus.SENT, now);
            notifications.priceRecommended(sellerEmail, product, decision.currentPrice(), decision.finalPrice(),
                    decision.reason());
        }
    }

    /** Competitor prices of the product, with the match reliability flag (only reliable ones drive decisions). */
    private List<CompetitorPrice> competitorPrices(PricingRule rule) {
        Set<Long> shopIds = rule.getCompetitors().isEmpty()
                ? shops.findByType(ShopType.MONITORED).stream().map(Shop::getId).collect(Collectors.toSet())
                : rule.getCompetitors().stream().map(Shop::getId).collect(Collectors.toSet());
        List<CompetitorPrice> result = new ArrayList<>();
        for (Offer offer : offers.findByProduct_IdAndShop_IdIn(rule.getProduct().getId(), shopIds)) {
            Optional<PriceRecord> latest = prices.findFirstByOffer_IdOrderByFetchedAtDesc(offer.getId());
            if (latest.isEmpty() || offer.getLastFetchedAt() == null) {
                continue;
            }
            boolean reliable = matches.findFirstByOffer_IdAndProduct_Id(offer.getId(), rule.getProduct().getId())
                    .map(m -> m.isReliableForPricing()).orElse(false);
            result.add(new CompetitorPrice(offer.getShop().getId(), offer.getShop().getName(), latest.get().getPrice(),
                    offer.getLastFetchedAt(), latest.get().isAvailable(), reliable));
        }
        return result;
    }

    /** Avoids sending the same message again while the lowest competitor price stays the same. */
    private boolean isRepeated(PricingRule rule, DecisionStatus status, BigDecimal lowest) {
        return decisions.findFirstByRule_IdOrderByCreatedAtDesc(rule.getId())
                .filter(last -> last.getStatus() == status)
                .filter(last -> last.getLowestCompetitorPrice().compareTo(lowest) == 0)
                .isPresent();
    }

    private void save(PricingRule rule, PricingDecision decision, BigDecimal newPrice, DecisionStatus status,
                      Instant now) {
        save(rule, decision, newPrice, status, now, decision.reason());
    }

    private void save(PricingRule rule, PricingDecision decision, BigDecimal newPrice, DecisionStatus status,
                      Instant now, String reason) {
        decisions.save(new PricingDecisionRecord(rule, decision.currentPrice(), newPrice,
                decision.lowestCompetitorPrice(), reason, status, now));
    }
}
