package pl.pricemonitor.core.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferMatcherTest {

    private static final List<ProductCandidate> CATALOG = List.of(
            new ProductCandidate(1, "Apple iPhone 16 128GB Czarny", "Apple", "iPhone 16", "2000000010007", "SIM-P01",
                    Map.of("storage", "128gb", "color", "czarny")),
            new ProductCandidate(2, "Apple iPhone 16 256GB Czarny", "Apple", "iPhone 16", "2000000010014", "SIM-P02",
                    Map.of("storage", "256gb", "color", "czarny")),
            new ProductCandidate(3, "Apple iPhone 16 128GB Biały", "Apple", "iPhone 16", "2000000010021", null,
                    Map.of("storage", "128gb", "color", "biały")),
            new ProductCandidate(7, "Sony PlayStation 5 Pro", "Sony", "PlayStation 5 Pro", "2000000010069", null, Map.of()),
            new ProductCandidate(8, "Sony PlayStation 5 Slim Digital", "Sony", "PlayStation 5 Slim Digital", null, null,
                    Map.of()),
            new ProductCandidate(10, "Nintendo Switch 2", "Nintendo", "Switch 2", null, null, Map.of()),
            new ProductCandidate(11, "Nintendo Switch 2 + Mario Kart World", "Nintendo", "Switch 2", null, null,
                    Map.of("bundle", "tak")),
            new ProductCandidate(13, "Sony WH-1000XM6 Czarne", "Sony", "WH-1000XM6", null, null, Map.of("color", "czarny")),
            new ProductCandidate(16, "Samsung QE55Q80D 55\" QLED", "Samsung", "QE55Q80D", null, null, Map.of("size", "55")),
            new ProductCandidate(17, "LG OLED55C4 55\" OLED", "LG", "OLED55C4", null, null, Map.of("size", "55")));

    private final OfferMatcher matcher = new OfferMatcher();

    private static OfferCandidate offer(String name, String brand, String gtin, Map<String, String> attributes) {
        return new OfferCandidate(name, brand, gtin, null, attributes, ProductCondition.NEW);
    }

    @Test
    void matchesByGtin() {
        MatchResult r = matcher.match(offer("Smartfon jakiś opis", null, "2000000010014", Map.of()), CATALOG).orElseThrow();

        assertEquals(2, r.productId());
        assertEquals(MatchMethod.IDENTIFIER, r.method());
        assertEquals(MatchStatus.AUTOMATIC, r.status());
        assertEquals(new BigDecimal("1.000"), r.confidence());
    }

    @Test
    void gtinWithConflictingVariantGoesToReview() {
        MatchResult r = matcher.match(offer("iPhone 16 256GB czarny", "Apple", "2000000010007", Map.of()), CATALOG)
                .orElseThrow();

        assertEquals(1, r.productId());
        assertEquals(MatchStatus.TO_REVIEW, r.status());
    }

    @Test
    void matchesByMpn() {
        OfferCandidate o = new OfferCandidate("Telefon", null, null, "sim-p01", Map.of(), ProductCondition.NEW);
        MatchResult r = matcher.match(o, CATALOG).orElseThrow();

        assertEquals(1, r.productId());
        assertEquals(MatchMethod.IDENTIFIER, r.method());
        assertEquals(new BigDecimal("0.970"), r.confidence());
    }

    @Test
    @DisplayName("Variants of the same model: storage and color decide")
    void distinguishesVariantsByAttributes() {
        assertEquals(2, matcher.match(offer("iPhone 16 256GB czarny", null, null, Map.of()), CATALOG).orElseThrow()
                .productId());
        assertEquals(3, matcher.match(offer("APPLE iPhone 16 128 GB", "Apple", null, Map.of("color", "white")), CATALOG)
                .orElseThrow().productId());
        MatchResult r = matcher.match(offer("Smartfon Apple iPhone 16 128GB Czarny", "Apple", null,
                Map.of("Kolor", "czarny", "Pamięć", "128 GB")), CATALOG).orElseThrow();
        assertEquals(1, r.productId());
        assertEquals(MatchMethod.ATTRIBUTES, r.method());
        assertEquals(MatchStatus.AUTOMATIC, r.status());
    }

    @Test
    void missingVariantAttributeGoesToReview() {
        MatchResult r = matcher.match(offer("Apple iPhone 16 128GB", "Apple", null, Map.of()), CATALOG).orElseThrow();

        assertEquals(MatchStatus.TO_REVIEW, r.status());
    }

    @Test
    void distinguishesBundleFromConsoleAlone() {
        assertEquals(10, matcher.match(offer("Nintendo Switch 2 - PROMOCJA", "Nintendo", null, Map.of()), CATALOG)
                .orElseThrow().productId());
        assertEquals(11, matcher.match(offer("NINTENDO Switch 2 zestaw z grą Mario Kart World", "Nintendo", null,
                Map.of()), CATALOG).orElseThrow().productId());
    }

    @Test
    void differentModelLineIsNotMatched() {
        assertEquals(8, matcher.match(offer("Konsola Sony PlayStation 5 Slim Digital", "Sony", null, Map.of()), CATALOG)
                .orElseThrow().productId());
        assertEquals(7, matcher.match(offer("Sony PlayStation 5 Pro (gwarancja PL)", "Sony", null, Map.of()), CATALOG)
                .orElseThrow().productId());
        assertTrue(matcher.match(offer("Pad Sony DualSense Edge", "Sony", null, Map.of()), CATALOG).isEmpty());
    }

    @Test
    void usedAndRefurbishedOffersAreSkipped() {
        assertTrue(matcher.match(offer("[Outlet] Apple iPhone 16 128GB Czarny - używany", "Apple", "2000000010007",
                Map.of()), CATALOG).isEmpty());
        OfferCandidate refurbished = new OfferCandidate("Sony PlayStation 5 Pro", "Sony", null, null, Map.of(),
                ProductCondition.REFURBISHED);
        assertTrue(matcher.match(refurbished, CATALOG).isEmpty());
    }

    @Test
    void brandMismatchIsNotMatched() {
        assertTrue(matcher.match(offer("OLED55C4 55\" OLED", "Samsung", null, Map.of()), CATALOG).isEmpty());
    }

    @Test
    void matchesPolishAdjectiveFormsAndScreenSize() {
        assertEquals(13, matcher.match(offer("Sony WH-1000XM6 czarne słuchawki", "Sony", null, Map.of()), CATALOG)
                .orElseThrow().productId());
        assertEquals(16, matcher.match(offer("SAMSUNG QE55Q80D 55 cali", "Samsung", null, Map.of("screen", "55")), CATALOG)
                .orElseThrow().productId());
    }

    @Test
    void nameSimilarityWithoutModelTokens() {
        List<ProductCandidate> catalog = List.of(
                new ProductCandidate(1, "Logitech MX Master 3S Grafitowa", "Logitech", "MX Master 3S", null, null, Map.of()));
        Optional<MatchResult> close = matcher.match(offer("Logitech MX Master3S grafitowa mysz", null, null, Map.of()),
                catalog);
        assertTrue(close.isPresent());
        assertEquals(MatchMethod.NAME, close.get().method());

        assertTrue(matcher.match(offer("Klawiatura mechaniczna", null, null, Map.of()), catalog).isEmpty());
    }

    @Test
    void nameMatchCloseToThresholdGoesToReview() {
        OfferMatcher strict = new OfferMatcher(new MatcherSettings(0.9, 0.5));
        List<ProductCandidate> catalog = List.of(
                new ProductCandidate(1, "Logitech MX Master 3S Grafitowa", "Logitech", "MX Master 3S", null, null, Map.of()));
        MatchResult r = strict.match(offer("Logitech MX Master3S grafitowa mysz", null, null, Map.of()), catalog)
                .orElseThrow();

        assertEquals(MatchStatus.TO_REVIEW, r.status());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new MatcherSettings(0.5, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new MatcherSettings(0, 0));
    }

    @Test
    void reliabilityPolicy() {
        assertTrue(MatchPolicy.reliableForPricing(MatchMethod.IDENTIFIER, MatchStatus.AUTOMATIC));
        assertTrue(MatchPolicy.reliableForPricing(MatchMethod.NAME, MatchStatus.APPROVED));
        assertFalse(MatchPolicy.reliableForPricing(MatchMethod.ATTRIBUTES, MatchStatus.AUTOMATIC));
        assertFalse(MatchPolicy.reliableForPricing(MatchMethod.IDENTIFIER, MatchStatus.TO_REVIEW));
        assertTrue(MatchPolicy.visibleInComparison(MatchStatus.AUTOMATIC));
        assertFalse(MatchPolicy.visibleInComparison(MatchStatus.TO_REVIEW));
    }
}
