package pl.pricemonitor.core.matching;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes names for comparison: lower case, no Polish diacritics, no punctuation, "128 GB" -> "128gb". */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern UNIT_GAP = Pattern.compile("(\\d)\\s+(gb|tb|mm|cali)\\b");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll(" ").trim();
        s = UNIT_GAP.matcher(s).replaceAll("$1$2");
        return s;
    }

    /** Identifier normalization for GTIN: digits only, without leading zeros (GTIN-12/13/14 are comparable). */
    public static String normalizeGtin(String gtin) {
        if (gtin == null) {
            return null;
        }
        String digits = gtin.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Identifier normalization for MPN: upper case letters and digits only. */
    public static String normalizeMpn(String mpn) {
        if (mpn == null) {
            return null;
        }
        String s = mpn.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return s.isEmpty() ? null : s;
    }
}
