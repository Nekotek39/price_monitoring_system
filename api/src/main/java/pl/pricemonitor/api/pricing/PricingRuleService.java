package pl.pricemonitor.api.pricing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.api.web.ConflictException;
import pl.pricemonitor.api.web.NotFoundException;
import pl.pricemonitor.core.pricing.PricingDecision;
import pl.pricemonitor.persistence.entity.AppUser;
import pl.pricemonitor.persistence.entity.PricingDecisionRecord;
import pl.pricemonitor.persistence.entity.PricingRule;
import pl.pricemonitor.persistence.entity.Product;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.entity.ShopType;
import pl.pricemonitor.persistence.entity.UserRole;
import pl.pricemonitor.persistence.repository.AppUserRepository;
import pl.pricemonitor.persistence.repository.PricingDecisionRepository;
import pl.pricemonitor.persistence.repository.PricingRuleRepository;
import pl.pricemonitor.persistence.repository.ProductRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Seller's configuration panel: CRUD of pricing rules and decision history. */
@Service
public class PricingRuleService {

    private final PricingRuleRepository rules;
    private final PricingDecisionRepository decisions;
    private final AppUserRepository users;
    private final ProductRepository products;
    private final ShopRepository shops;
    private final PricingService pricing;

    public PricingRuleService(PricingRuleRepository rules, PricingDecisionRepository decisions, AppUserRepository users,
                              ProductRepository products, ShopRepository shops, PricingService pricing) {
        this.rules = rules;
        this.decisions = decisions;
        this.users = users;
        this.products = products;
        this.shops = shops;
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public List<PricingRuleView> list(Long sellerId) {
        seller(sellerId);
        return rules.findByOwner_IdOrderByIdAsc(sellerId).stream().map(PricingRuleService::view).toList();
    }

    @Transactional(readOnly = true)
    public PricingRuleView get(Long sellerId, Long ruleId) {
        return view(rule(sellerId, ruleId));
    }

    @Transactional
    public PricingRuleView create(Long sellerId, PricingRuleRequest request) {
        AppUser seller = seller(sellerId);
        if (rules.existsByOwner_IdAndProduct_Id(sellerId, request.productId())) {
            throw new ConflictException("Reguła dla tego produktu już istnieje");
        }
        PricingRule rule = new PricingRule(seller, product(request.productId()), request.shopProductId());
        apply(rule, request);
        return view(rules.save(rule));
    }

    @Transactional
    public PricingRuleView update(Long sellerId, Long ruleId, PricingRuleRequest request) {
        PricingRule rule = rule(sellerId, ruleId);
        if (!rule.getProduct().getId().equals(request.productId())) {
            throw new IllegalArgumentException("Nie można zmienić produktu reguły - utwórz nową regułę");
        }
        rule.setShopProductId(request.shopProductId());
        apply(rule, request);
        return view(rule);
    }

    @Transactional
    public void delete(Long sellerId, Long ruleId) {
        rules.delete(rule(sellerId, ruleId));
    }

    @Transactional
    public PricingDecision evaluateNow(Long sellerId, Long ruleId) {
        return pricing.evaluate(rule(sellerId, ruleId));
    }

    @Transactional(readOnly = true)
    public List<DecisionView> decisions(Long sellerId) {
        seller(sellerId);
        return decisions.findTop100ByRule_Owner_IdOrderByCreatedAtDesc(sellerId).stream()
                .map(PricingRuleService::view).toList();
    }

    private void apply(PricingRule rule, PricingRuleRequest request) {
        if (request.maxPrice() != null && request.maxPrice().compareTo(request.minPrice()) < 0) {
            throw new IllegalArgumentException("Cena maksymalna nie może być niższa niż minimalna");
        }
        Set<Shop> competitors = new HashSet<>();
        if (request.competitorShopIds() != null && !request.competitorShopIds().isEmpty()) {
            List<Shop> found = shops.findAllById(request.competitorShopIds());
            if (found.size() != request.competitorShopIds().size()
                    || found.stream().anyMatch(s -> s.getType() != ShopType.MONITORED)) {
                throw new IllegalArgumentException("Konkurentami mogą być tylko monitorowane sklepy");
            }
            competitors.addAll(found);
        }
        rule.setStrategy(request.strategy());
        rule.setParameters(request.parameters());
        rule.setMinPrice(request.minPrice());
        rule.setMaxPrice(request.maxPrice());
        rule.setMaxChangePercent(request.maxChangePercent());
        rule.setMinIntervalMinutes(request.minIntervalMinutes());
        rule.setMode(request.mode());
        rule.setActive(request.active());
        rule.setCompetitors(competitors);
    }

    private AppUser seller(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("Użytkownik " + id + " nie istnieje"));
        if (user.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("Użytkownik " + id + " nie jest sprzedawcą");
        }
        return user;
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produkt " + id + " nie istnieje"));
    }

    private PricingRule rule(Long sellerId, Long ruleId) {
        return rules.findByIdAndOwner_Id(ruleId, sellerId)
                .orElseThrow(() -> new NotFoundException("Reguła " + ruleId + " nie istnieje"));
    }

    private static PricingRuleView view(PricingRule r) {
        List<Shop> competitors = r.getCompetitors().stream().sorted(Comparator.comparing(Shop::getId)).toList();
        return new PricingRuleView(r.getId(), r.getProduct().getId(), r.getProduct().getName(), r.getShopProductId(),
                r.getStrategy().name(), r.getParameters(), r.getMinPrice(), r.getMaxPrice(), r.getMaxChangePercent(),
                r.getMinIntervalMinutes(), r.getMode().name(), r.isActive(),
                competitors.stream().map(Shop::getId).toList(), competitors.stream().map(Shop::getName).toList());
    }

    private static DecisionView view(PricingDecisionRecord d) {
        return new DecisionView(d.getId(), d.getRule().getId(), d.getRule().getProduct().getName(), d.getPreviousPrice(),
                d.getNewPrice(), d.getLowestCompetitorPrice(), d.getReason(), d.getStatus().name(), d.getCreatedAt());
    }
}
