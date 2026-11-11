package pl.pricemonitor.core.matching;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAndAttributesTest {

    @Test
    void normalizesText() {
        assertEquals("smartfon apple iphone 16 128gb bialy", TextNormalizer.normalize("Smartfon Apple iPhone 16, 128 GB – Biały!"));
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("zolty lodz", TextNormalizer.normalize("Żółty Łódź"));
    }

    @Test
    void normalizesIdentifiers() {
        assertEquals("5901234123457", TextNormalizer.normalizeGtin("05901234123457"));
        assertEquals("5901234123457", TextNormalizer.normalizeGtin("590-1234-123457"));
        assertNull(TextNormalizer.normalizeGtin("  "));
        assertNull(TextNormalizer.normalizeGtin(null));
        assertEquals("MQ2A3ZMA", TextNormalizer.normalizeMpn("mq2a3zm/a"));
        assertNull(TextNormalizer.normalizeMpn("--"));
        assertNull(TextNormalizer.normalizeMpn(null));
    }

    @Test
    void trigramSimilarityBehavesLikePgTrgm() {
        assertEquals(1.0, TrigramSimilarity.similarity("iPhone 16", "IPHONE 16"), 1e-9);
        assertEquals(0.0, TrigramSimilarity.similarity("", "abc"), 1e-9);
        // pg_trgm: similarity('word', 'words') = 0.5714286 (4 common / 7 total)
        assertEquals(4.0 / 7.0, TrigramSimilarity.similarity("word", "words"), 1e-9);
        assertTrue(TrigramSimilarity.similarity("Apple iPhone 16 128GB", "iPhone 16 128 GB Apple") > 0.9);
    }

    @Test
    void extractsAttributesFromName() {
        Map<String, String> a = VariantAttributes.extractFromName("Lenovo Legion 5 32GB 1TB Szary");
        assertEquals("1tb", a.get(VariantAttributes.STORAGE));
        assertEquals("szary", a.get(VariantAttributes.COLOR));
        assertEquals("nie", a.get(VariantAttributes.BUNDLE));

        assertEquals("55", VariantAttributes.extractFromName("Telewizor LG OLED55C4 55\" OLED").get(VariantAttributes.SIZE));
        assertEquals("65", VariantAttributes.extractFromName("Telewizor 65 cali").get(VariantAttributes.SIZE));
        assertEquals("46mm", VariantAttributes.extractFromName("Apple Watch Series 10 46 mm").get(VariantAttributes.SIZE));
        assertEquals("tak", VariantAttributes.extractFromName("Konsola + gra").get(VariantAttributes.BUNDLE));
        assertNull(VariantAttributes.extractFromName("Xiaomi Redmi Note 14").get(VariantAttributes.COLOR));
        assertNull(VariantAttributes.extractFromName(null).get(VariantAttributes.STORAGE));
    }

    @Test
    void normalizesFeedAttributes() {
        Map<String, String> raw = new HashMap<>();
        raw.put("Kolor", "Czarna");
        raw.put("Pamięć wbudowana", "256 GB");
        raw.put("Przekątna ekranu", "55 cali");
        raw.put("zestaw", "tak");
        raw.put("waga", "200 g");
        raw.put("region", "PL");
        raw.put("color", "  ");
        Map<String, String> n = VariantAttributes.normalize(raw);

        assertEquals("czarny", n.get(VariantAttributes.COLOR));
        assertEquals("256gb", n.get(VariantAttributes.STORAGE));
        assertEquals("55", n.get(VariantAttributes.SIZE));
        assertEquals("tak", n.get(VariantAttributes.BUNDLE));
        assertEquals("pl", n.get(VariantAttributes.REGION));
        assertEquals(4 + 1, n.size());
        assertTrue(VariantAttributes.normalize(null).isEmpty());
        assertEquals("mietowy", VariantAttributes.normalize(Map.of("kolor", "miętowy")).get(VariantAttributes.COLOR));
        assertEquals("nie", VariantAttributes.normalize(Map.of("bundle", "no")).get(VariantAttributes.BUNDLE));
        assertEquals("duzo", VariantAttributes.normalize(Map.of("storage", "dużo")).get(VariantAttributes.STORAGE));
    }

    @Test
    void comparesAttributes() {
        assertEquals(VariantAttributes.Comparison.EQUAL,
                VariantAttributes.compare(Map.of("color", "czarny"), Map.of("color", "czarny", "storage", "128gb")));
        assertEquals(VariantAttributes.Comparison.INCOMPLETE,
                VariantAttributes.compare(Map.of("color", "czarny"), Map.of()));
        assertEquals(VariantAttributes.Comparison.CONFLICT,
                VariantAttributes.compare(Map.of("color", "czarny"), Map.of("color", "bialy")));
        assertEquals(VariantAttributes.Comparison.CONFLICT,
                VariantAttributes.compare(Map.of(), Map.of("bundle", "tak")));
    }

    @Test
    void readsCondition() {
        assertEquals(ProductCondition.NEW, ProductCondition.fromFeedValue("nowy"));
        assertEquals(ProductCondition.USED, ProductCondition.fromFeedValue("Używany"));
        assertEquals(ProductCondition.REFURBISHED, ProductCondition.fromFeedValue("refurbished"));
        assertEquals(ProductCondition.UNKNOWN, ProductCondition.fromFeedValue("?"));
        assertEquals(ProductCondition.UNKNOWN, ProductCondition.fromFeedValue(null));
        assertEquals(ProductCondition.USED, ProductCondition.resolve("new", "[Outlet] Telefon"));
        assertEquals(ProductCondition.REFURBISHED, ProductCondition.resolve(null, "Konsola odnowiona"));
        assertEquals(ProductCondition.NEW, ProductCondition.resolve(null, "Konsola"));
        assertEquals(ProductCondition.USED, ProductCondition.resolve("used", "Konsola"));
    }
}
