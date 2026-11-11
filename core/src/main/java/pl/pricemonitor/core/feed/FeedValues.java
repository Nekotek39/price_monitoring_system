package pl.pricemonitor.core.feed;

import java.math.BigDecimal;

/** Shared value parsing for feed parsers. */
public final class FeedValues {

    private FeedValues() {
    }

    /** Parses "1 299,00", "1299.00" or "1299"; {@code null} or blank gives {@code null}. */
    public static BigDecimal decimalOrNull(String raw, String field, String offerId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().replace(" ", "").replace(" ", "").replace(',', '.');
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.signum() < 0) {
                throw new FeedParseException("Negative " + field + " in offer " + offerId + ": " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new FeedParseException("Invalid " + field + " in offer " + offerId + ": " + raw, e);
        }
    }

    public static BigDecimal requiredDecimal(String raw, String field, String offerId) {
        BigDecimal value = decimalOrNull(raw, field, offerId);
        if (value == null) {
            throw new FeedParseException("Missing " + field + " in offer " + offerId);
        }
        return value;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "1", "true", "tak", "yes" -> true. */
    public static boolean flag(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String v = raw.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("tak") || v.equals("yes");
    }
}
