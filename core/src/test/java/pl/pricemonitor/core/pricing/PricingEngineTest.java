package pl.pricemonitor.core.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingEngineTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private final PricingEngine engine = new PricingEngine();

    private static BigDecimal pln(String value) {
        return new BigDecimal(value);
    }

    private static CompetitorPrice competitor(long shopId, String price) {
        return new CompetitorPrice(shopId, "Sklep " + shopId, pln(price), NOW.minusSeconds(60), true, true);
    }

    private static PricingRuleSettings rule(StrategyType strategy, Map<String, String> params, String min, String max,
                                            String maxChange) {
        return new PricingRuleSettings(strategy, params, pln(min), max == null ? null : pln(max), pln(maxChange),
                Duration.ofMinutes(30), Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Example from section C1.1: competitor below the minimum price -> price is kept")
    void keepsPriceWhenCompetitorGoesBelowMinimum() {
        PricingRuleSettings rule = rule(StrategyType.UNDERCUT_AMOUNT, Map.of("amount", "1.00"), "1350.00", null, "20");
        PricingInput input = new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1549.00"), competitor(2, "1299.00"), competitor(3, "1569.00")), rule, NOW, null);

        PricingDecision decision = engine.decide(input);

        assertEquals(DecisionOutcome.KEEP_BELOW_MINIMUM, decision.outcome());
        assertEquals(pln("1298.00"), decision.proposedPrice());
        assertEquals(pln("1500.00"), decision.finalPrice());
        assertEquals(pln("1299.00"), decision.lowestCompetitorPrice());
        assertFalse(decision.changesPrice());
        assertTrue(decision.reason().contains("cena minimalna"));
    }

    @Test
    void undercutsLowestCompetitorByAmount() {
        PricingRuleSettings rule = rule(StrategyType.UNDERCUT_AMOUNT, Map.of("amount", "1.00"), "1350.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1549.00"), competitor(2, "1479.00")), rule, NOW, null));

        assertEquals(DecisionOutcome.CHANGE, decision.outcome());
        assertEquals(pln("1478.00"), decision.finalPrice());
        assertTrue(decision.changesPrice());
    }

    @Test
    void limitsSingleChangeToMaxPercent() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "5");
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1300.00")), rule, NOW, null));

        assertEquals(DecisionOutcome.CHANGE, decision.outcome());
        assertEquals(pln("1425.00"), decision.finalPrice());
        assertTrue(decision.reason().contains("5%"));
    }

    @Test
    void limitsPriceIncreaseToMaxPercent() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "5");
        PricingDecision decision = engine.decide(new PricingInput(pln("1000.00"),
                List.of(competitor(1, "1300.00")), rule, NOW, null));

        assertEquals(pln("1050.00"), decision.finalPrice());
        assertTrue(decision.reason().contains("podwyżkę"));
    }

    @Test
    void capsPriceAtMaximum() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", "1520.00", "50");
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1600.00")), rule, NOW, null));

        assertEquals(DecisionOutcome.CHANGE, decision.outcome());
        assertEquals(pln("1520.00"), decision.finalPrice());
    }

    @Test
    void noChangeWhenProposalEqualsCurrentPrice() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1499.00"),
                List.of(competitor(1, "1499.00")), rule, NOW, null));

        assertEquals(DecisionOutcome.NO_CHANGE, decision.outcome());
        assertEquals(pln("1499.00"), decision.finalPrice());
    }

    @Test
    void respectsMinimumIntervalBetweenChanges() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1450.00")), rule, NOW, NOW.minus(Duration.ofMinutes(10))));

        assertEquals(DecisionOutcome.TOO_SOON, decision.outcome());
        assertEquals(pln("1500.00"), decision.finalPrice());
    }

    @Test
    void allowsChangeAfterMinimumInterval() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1450.00")), rule, NOW, NOW.minus(Duration.ofMinutes(31))));

        assertEquals(DecisionOutcome.CHANGE, decision.outcome());
    }

    @Test
    void requestsFreshDataWhenPricesAreStale() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        CompetitorPrice old = new CompetitorPrice(7, "Stary", pln("1400.00"), NOW.minus(Duration.ofHours(2)), true, true);
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1450.00"), old), rule, NOW, null));

        assertEquals(DecisionOutcome.STALE_DATA, decision.outcome());
        assertEquals(List.of(7L), decision.staleShopIds());
    }

    @Test
    void ignoresUnreliableMatchesAndUnavailableOffers() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        CompetitorPrice nameOnly = new CompetitorPrice(2, "B", pln("1100.00"), NOW, true, false);
        CompetitorPrice unavailable = new CompetitorPrice(3, "C", pln("1200.00"), NOW, false, true);
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"),
                List.of(competitor(1, "1460.00"), nameOnly, unavailable), rule, NOW, null));

        assertEquals(pln("1460.00"), decision.finalPrice());
        assertEquals(pln("1460.00"), decision.lowestCompetitorPrice());
    }

    @Test
    void noDataWithoutReliableCompetitorPrices() {
        PricingRuleSettings rule = rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", null, "10");
        CompetitorPrice nameOnly = new CompetitorPrice(2, "B", pln("1100.00"), NOW, true, false);
        PricingDecision decision = engine.decide(new PricingInput(pln("1500.00"), List.of(nameOnly), rule, NOW, null));

        assertEquals(DecisionOutcome.NO_DATA, decision.outcome());
        assertNull(decision.lowestCompetitorPrice());
    }

    @Test
    void undercutsByPercent() {
        PricingRuleSettings rule = rule(StrategyType.UNDERCUT_PERCENT, Map.of("percent", "2"), "100.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1050.00"),
                List.of(competitor(1, "1000.00")), rule, NOW, null));

        assertEquals(pln("980.00"), decision.finalPrice());
    }

    @Test
    void averageBandMovesPriceToNearestEdge() {
        PricingRuleSettings rule = rule(StrategyType.AVERAGE_BAND, Map.of("band", "5"), "100.00", null, "50");
        List<CompetitorPrice> prices = List.of(competitor(1, "900.00"), competitor(2, "1100.00"));

        assertEquals(pln("1050.00"), engine.decide(new PricingInput(pln("1200.00"), prices, rule, NOW, null)).finalPrice());
        assertEquals(pln("950.00"), engine.decide(new PricingInput(pln("800.00"), prices, rule, NOW, null)).finalPrice());
        assertEquals(DecisionOutcome.NO_CHANGE,
                engine.decide(new PricingInput(pln("1010.00"), prices, rule, NOW, null)).outcome());
    }

    @Test
    void rejectsInvalidParameters() {
        PricingRuleSettings negative = rule(StrategyType.UNDERCUT_AMOUNT, Map.of("amount", "-5"), "100.00", null, "10");
        PricingRuleSettings text = rule(StrategyType.UNDERCUT_PERCENT, Map.of("percent", "abc"), "100.00", null, "10");
        List<CompetitorPrice> prices = List.of(competitor(1, "1000.00"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.decide(new PricingInput(pln("1000.00"), prices, negative, NOW, null)));
        assertThrows(IllegalArgumentException.class,
                () -> engine.decide(new PricingInput(pln("1000.00"), prices, text, NOW, null)));
    }

    @Test
    void rejectsMaxPriceBelowMinPrice() {
        assertThrows(IllegalArgumentException.class,
                () -> rule(StrategyType.MATCH_LOWEST, Map.of(), "1000.00", "900.00", "10"));
    }

    @Test
    void usesDefaultParameterWhenMissing() {
        PricingRuleSettings rule = rule(StrategyType.UNDERCUT_AMOUNT, Map.of(), "100.00", null, "10");
        PricingDecision decision = engine.decide(new PricingInput(pln("1000.00"),
                List.of(competitor(1, "990.00")), rule, NOW, null));

        assertEquals(pln("989.00"), decision.finalPrice());
    }

    @Test
    void failsWithoutRegisteredStrategy() {
        PricingEngine onlyLowest = new PricingEngine(List.of(new MatchLowestStrategy()), List.of());
        PricingRuleSettings rule = rule(StrategyType.UNDERCUT_AMOUNT, Map.of(), "100.00", null, "10");

        assertThrows(IllegalStateException.class, () -> onlyLowest.decide(new PricingInput(pln("1000.00"),
                List.of(competitor(1, "990.00")), rule, NOW, null)));
    }

    @Test
    void formatsMoneyForMessages() {
        assertEquals("1 350,00 zł", Money.format(pln("1350")));
        assertEquals("999,90 zł", Money.format(pln("999.9")));
        assertEquals("1 234 567,00 zł", Money.format(pln("1234567")));
        assertEquals("-1 500,00 zł", Money.format(pln("-1500")));
        assertEquals("-", Money.format(null));
    }
}
