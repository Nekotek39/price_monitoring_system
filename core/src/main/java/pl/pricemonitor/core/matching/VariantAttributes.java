package pl.pricemonitor.core.matching;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variant attributes that distinguish otherwise identical models: color, storage, size (screen / case),
 * regional version and bundle contents. Values are normalized so that "128 GB", "128GB" and "128gb"
 * or "Czarne", "czarny" and "black" compare equal.
 */
public final class VariantAttributes {

    public static final String COLOR = "color";
    public static final String STORAGE = "storage";
    public static final String SIZE = "size";
    public static final String REGION = "region";
    public static final String BUNDLE = "bundle";

    /** Outcome of comparing product and offer attributes. */
    public enum Comparison {
        /** All attributes known on both sides are equal and the offer has every attribute of the product. */
        EQUAL,
        /** No conflict, but the offer lacks an attribute the product has, so the pair cannot be confirmed. */
        INCOMPLETE,
        /** At least one attribute differs. */
        CONFLICT
    }

    private static final Map<String, String> KEY_ALIASES = Map.ofEntries(
            Map.entry("color", COLOR), Map.entry("colour", COLOR), Map.entry("kolor", COLOR),
            Map.entry("storage", STORAGE), Map.entry("pamiec", STORAGE), Map.entry("pojemnosc", STORAGE),
            Map.entry("pamiec wbudowana", STORAGE), Map.entry("dysk", STORAGE),
            Map.entry("size", SIZE), Map.entry("screen", SIZE), Map.entry("przekatna", SIZE),
            Map.entry("przekatna ekranu", SIZE), Map.entry("rozmiar", SIZE), Map.entry("koperta", SIZE),
            Map.entry("region", REGION), Map.entry("wersja", REGION), Map.entry("dystrybucja", REGION),
            Map.entry("bundle", BUNDLE), Map.entry("zestaw", BUNDLE));

    /** Polish color word stems (after diacritics removal) mapped to a canonical masculine form. */
    private static final List<Map.Entry<String, String>> COLOR_STEMS = List.of(
            Map.entry("czarn", "czarny"), Map.entry("bial", "bialy"), Map.entry("granatow", "granatowy"),
            Map.entry("tytanow", "tytanowy"), Map.entry("zielon", "zielony"), Map.entry("grafitow", "grafitowy"),
            Map.entry("srebrn", "srebrny"), Map.entry("zlot", "zloty"), Map.entry("niebiesk", "niebieski"),
            Map.entry("rozow", "rozowy"), Map.entry("fioletow", "fioletowy"), Map.entry("czerwon", "czerwony"),
            Map.entry("szar", "szary"));

    /** English color words (exact match only, so that e.g. "redmi" is not read as red). */
    private static final Map<String, String> COLOR_WORDS = Map.ofEntries(
            Map.entry("black", "czarny"), Map.entry("white", "bialy"), Map.entry("navy", "granatowy"),
            Map.entry("titanium", "tytanowy"), Map.entry("green", "zielony"), Map.entry("graphite", "grafitowy"),
            Map.entry("silver", "srebrny"), Map.entry("gold", "zloty"), Map.entry("blue", "niebieski"),
            Map.entry("pink", "rozowy"), Map.entry("purple", "fioletowy"), Map.entry("red", "czerwony"),
            Map.entry("gray", "szary"), Map.entry("grey", "szary"));

    private static final Pattern STORAGE_PATTERN = Pattern.compile("\\b(\\d{1,4})(gb|tb)\\b");
    private static final Pattern INCH_PATTERN = Pattern.compile("(\\d{2,3}(?:[.,]\\d)?)\\s*(?:\"|”|''|cali|cal|inch)");
    private static final Pattern MM_PATTERN = Pattern.compile("\\b(\\d{2})mm\\b");
    private static final List<String> BUNDLE_HINTS = List.of(" zestaw ", " bundle ", " z gra ", " + ");

    private VariantAttributes() {
    }

    /** Maps feed-specific keys to canonical keys and normalizes values; unknown keys are dropped. */
    public static Map<String, String> normalize(Map<String, String> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        raw.forEach((key, value) -> {
            String canonical = KEY_ALIASES.get(TextNormalizer.normalize(key));
            if (canonical != null && value != null && !value.isBlank()) {
                String normalized = normalizeValue(canonical, value);
                if (normalized != null) {
                    result.put(canonical, normalized);
                }
            }
        });
        return result;
    }

    /** Extracts attributes from a product or offer name. */
    public static Map<String, String> extractFromName(String name) {
        Map<String, String> result = new LinkedHashMap<>();
        String n = TextNormalizer.normalize(name);

        Matcher storage = STORAGE_PATTERN.matcher(n);
        BigDecimal bestGb = null;
        String bestValue = null;
        while (storage.find()) {
            BigDecimal gb = new BigDecimal(storage.group(1)).multiply(
                    storage.group(2).equals("tb") ? BigDecimal.valueOf(1024) : BigDecimal.ONE);
            // Laptops list RAM and disk ("16GB 512GB"): the larger value is the storage.
            if (bestGb == null || gb.compareTo(bestGb) > 0) {
                bestGb = gb;
                bestValue = storage.group(1) + storage.group(2);
            }
        }
        if (bestValue != null) {
            result.put(STORAGE, bestValue);
        }

        Matcher inch = INCH_PATTERN.matcher(name == null ? "" : name.toLowerCase());
        if (inch.find()) {
            result.put(SIZE, inch.group(1).replace(',', '.'));
        } else {
            Matcher mm = MM_PATTERN.matcher(n);
            if (mm.find()) {
                result.put(SIZE, mm.group(1) + "mm");
            }
        }

        for (String word : n.split(" ")) {
            String color = colorOf(word);
            if (color != null) {
                result.put(COLOR, color);
                break;
            }
        }

        String padded = " " + n + " ";
        String rawPadded = " " + (name == null ? "" : name.toLowerCase()) + " ";
        boolean bundle = BUNDLE_HINTS.stream().anyMatch(h -> padded.contains(h) || rawPadded.contains(h));
        result.put(BUNDLE, bundle ? "tak" : "nie");
        return result;
    }

    /** Explicit attributes (from the feed or catalog) take precedence over attributes read from the name. */
    public static Map<String, String> combine(Map<String, String> explicitRaw, String name) {
        Map<String, String> result = extractFromName(name);
        result.putAll(normalize(explicitRaw));
        return result;
    }

    public static Comparison compare(Map<String, String> product, Map<String, String> offer) {
        boolean incomplete = false;
        for (Map.Entry<String, String> e : product.entrySet()) {
            String offerValue = offer.get(e.getKey());
            if (offerValue == null) {
                incomplete = true;
            } else if (!offerValue.equals(e.getValue())) {
                return Comparison.CONFLICT;
            }
        }
        for (Map.Entry<String, String> e : offer.entrySet()) {
            if (!product.containsKey(e.getKey()) && BUNDLE.equals(e.getKey()) && "tak".equals(e.getValue())) {
                return Comparison.CONFLICT;
            }
        }
        return incomplete ? Comparison.INCOMPLETE : Comparison.EQUAL;
    }

    static String normalizeValue(String key, String value) {
        String v = TextNormalizer.normalize(value);
        return switch (key) {
            case COLOR -> {
                String color = null;
                for (String word : v.split(" ")) {
                    color = colorOf(word);
                    if (color != null) {
                        break;
                    }
                }
                yield color != null ? color : v;
            }
            case STORAGE -> {
                Matcher m = STORAGE_PATTERN.matcher(v);
                yield m.find() ? m.group(1) + m.group(2) : v;
            }
            case SIZE -> v.replace(" ", "").replace("cali", "").replace("cal", "");
            case BUNDLE -> v.startsWith("t") || v.startsWith("y") ? "tak" : "nie";
            default -> v;
        };
    }

    private static String colorOf(String word) {
        String english = COLOR_WORDS.get(word);
        if (english != null) {
            return english;
        }
        for (Map.Entry<String, String> stem : COLOR_STEMS) {
            if (word.startsWith(stem.getKey())) {
                return stem.getValue();
            }
        }
        return null;
    }
}
