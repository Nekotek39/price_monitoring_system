package pl.pricemonitor.api.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.pricemonitor.api.jobs.FetchJobScheduler;
import pl.pricemonitor.api.notification.NotificationService;
import pl.pricemonitor.core.feed.FeedFormat;
import pl.pricemonitor.core.matching.MatchMethod;
import pl.pricemonitor.core.matching.MatchStatus;
import pl.pricemonitor.core.pricing.DecisionOutcome;
import pl.pricemonitor.core.pricing.PricingDecision;
import pl.pricemonitor.core.pricing.PricingEngine;
import pl.pricemonitor.core.pricing.PricingMode;
import pl.pricemonitor.core.pricing.StrategyType;
import pl.pricemonitor.persistence.entity.AppUser;
import pl.pricemonitor.persistence.entity.DecisionStatus;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.PriceRecord;
import pl.pricemonitor.persistence.entity.PriceSource;
import pl.pricemonitor.persistence.entity.PricingDecisionRecord;
import pl.pricemonitor.persistence.entity.PricingRule;
import pl.pricemonitor.persistence.entity.Product;
import pl.pricemonitor.persistence.entity.ProductMatch;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.entity.ShopType;
import pl.pricemonitor.persistence.entity.UserRole;
import pl.pricemonitor.persistence.repository.OfferRepository;
import pl.pricemonitor.persistence.repository.PriceRecordRepository;
import pl.pricemonitor.persistence.repository.PricingDecisionRepository;
import pl.pricemonitor.persistence.repository.PricingRuleRepository;
import pl.pricemonitor.persistence.repository.ProductMatchRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Execution of pricing decisions (the decision logic itself is tested in the core module). */
class PricingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private final PricingRuleRepository rules = mock(PricingRuleRepository.class);
    private final PricingDecisionRepository decisions = mock(PricingDecisionRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final PriceRecordRepository prices = mock(PriceRecordRepository.class);
    private final ProductMatchRepository matches = mock(ProductMatchRepository.class);
    private final ShopRepository shops = mock(ShopRepository.class);
    private final SellerShopClient sellerClient = mock(SellerShopClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FetchJobScheduler jobScheduler = mock(FetchJobScheduler.class);
    private final SystemSettings settings = mock(SystemSettings.class);

    private PricingService service;
    private Product product;
    private Shop sellerShop;
    private final List<Offer> competitorOffers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PricingService(rules, decisions, offers, prices, matches, shops, new PricingEngine(), sellerClient,
                notifications, jobScheduler, settings, Clock.fixed(NOW, ZoneOffset.UTC));
        when(settings.maxDataAge()).thenReturn(Duration.ofMinutes(30));
        product = new Product("Sony WH-1000XM6 Czarne", "Sony", "WH-1000XM6", "Słuchawki");
        ReflectionTestUtils.setField(product, "id", 13L);
        sellerShop = new Shop("Mój Sklep", ShopType.SELLER, null, null);
        ReflectionTestUtils.setField(sellerShop, "id", 6L);
        when(shops.findByOwner_IdAndType(any(), eq(ShopType.SELLER))).thenReturn(List.of(sellerShop));
        when(sellerClient.currentPrice(sellerShop, "MS-001")).thenReturn(new BigDecimal("1500.00"));
        when(offers.findByProduct_IdAndShop_IdIn(any(), anyCollection())).thenReturn(competitorOffers);
    }

    private PricingRule rule(PricingMode mode) {
        AppUser seller = new AppUser("sprzedawca@test.pl", "{noop}x", UserRole.SELLER);
        PricingRule rule = new PricingRule(seller, product, "MS-001");
        ReflectionTestUtils.setField(rule, "id", 1L);
        rule.setStrategy(StrategyType.UNDERCUT_AMOUNT);
        rule.setParameters(Map.of("amount", "1.00"));
        rule.setMinPrice(new BigDecimal("1350.00"));
        rule.setMaxChangePercent(new BigDecimal("10"));
        rule.setMinIntervalMinutes(30);
        rule.setMode(mode);
        rule.setCompetitors(Set.of(shop(1), shop(2), shop(3)));
        return rule;
    }

    private static Shop shop(long id) {
        Shop shop = new Shop("Sklep " + id, ShopType.MONITORED, "http://localhost/" + id, FeedFormat.JSON);
        ReflectionTestUtils.setField(shop, "id", id);
        return shop;
    }

    private Offer competitor(long shopId, String price, Instant fetchedAt) {
        Offer offer = new Offer(shop(shopId), "O-" + shopId);
        ReflectionTestUtils.setField(offer, "id", 100 + shopId);
        offer.setProduct(product);
        offer.setLastFetchedAt(fetchedAt);
        when(prices.findFirstByOffer_IdOrderByFetchedAtDesc(100 + shopId)).thenReturn(Optional.of(
                new PriceRecord(offer, new BigDecimal(price), BigDecimal.ZERO, true, PriceSource.CYCLIC, fetchedAt)));
        when(matches.findFirstByOffer_IdAndProduct_Id(100 + shopId, 13L)).thenReturn(Optional.of(
                new ProductMatch(offer, product, MatchMethod.IDENTIFIER, BigDecimal.ONE, MatchStatus.AUTOMATIC)));
        return offer;
    }

    @Test
    @DisplayName("Example from C1.1: competitor at 1 299 zł, minimum 1 350 zł -> price kept, seller informed")
    void keepsPriceAndNotifiesWhenCompetitorBelowMinimum() {
        PricingRule rule = rule(PricingMode.AUTOMATIC);
        competitorOffers.addAll(List.of(competitor(1, "1549.00", NOW), competitor(2, "1299.00", NOW),
                competitor(3, "1569.00", NOW)));

        PricingDecision decision = service.evaluate(rule);

        assertEquals(DecisionOutcome.KEEP_BELOW_MINIMUM, decision.outcome());
        ArgumentCaptor<PricingDecisionRecord> saved = ArgumentCaptor.forClass(PricingDecisionRecord.class);
        verify(decisions).save(saved.capture());
        assertEquals(DecisionStatus.KEPT, saved.getValue().getStatus());
        assertEquals(new BigDecimal("1500.00"), saved.getValue().getNewPrice());
        verify(notifications).competitorBelowMinimum(eq("sprzedawca@test.pl"), anyString(), eq(new BigDecimal("1299.00")),
                eq(new BigDecimal("1350.00")), anyString());
        verify(sellerClient, never()).updatePrice(any(), anyString(), any());
    }

    @Test
    void automaticModeChangesPriceInSellerShop() {
        PricingRule rule = rule(PricingMode.AUTOMATIC);
        competitorOffers.addAll(List.of(competitor(1, "1549.00", NOW), competitor(2, "1479.00", NOW)));

        PricingDecision decision = service.evaluate(rule);

        assertEquals(DecisionOutcome.CHANGE, decision.outcome());
        verify(sellerClient).updatePrice(sellerShop, "MS-001", new BigDecimal("1478.00"));
        ArgumentCaptor<PricingDecisionRecord> saved = ArgumentCaptor.forClass(PricingDecisionRecord.class);
        verify(decisions).save(saved.capture());
        assertEquals(DecisionStatus.APPLIED, saved.getValue().getStatus());
        verify(notifications).priceChanged(eq("sprzedawca@test.pl"), anyString(), eq(new BigDecimal("1500.00")),
                eq(new BigDecimal("1478.00")), anyString());
    }

    @Test
    void notifyModeOnlySendsRecommendation() {
        PricingRule rule = rule(PricingMode.NOTIFY);
        competitorOffers.add(competitor(1, "1479.00", NOW));

        service.evaluate(rule);

        verify(sellerClient, never()).updatePrice(any(), anyString(), any());
        verify(notifications).priceRecommended(eq("sprzedawca@test.pl"), anyString(), eq(new BigDecimal("1500.00")),
                eq(new BigDecimal("1478.00")), anyString());
    }

    @Test
    void staleDataTriggersOnDemandFetching() {
        PricingRule rule = rule(PricingMode.AUTOMATIC);
        competitorOffers.add(competitor(1, "1479.00", NOW.minus(Duration.ofHours(2))));
        when(shops.findAllById(any())).thenReturn(List.of(competitorOffers.get(0).getShop()));

        PricingDecision decision = service.evaluate(rule);

        assertEquals(DecisionOutcome.STALE_DATA, decision.outcome());
        verify(jobScheduler).requestOnDemand(eq(product), anyCollection());
        verify(sellerClient, never()).updatePrice(any(), anyString(), any());
    }
}
