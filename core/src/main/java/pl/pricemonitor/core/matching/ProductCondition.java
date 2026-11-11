package pl.pricemonitor.core.matching;

/** Condition of the offered item. Only NEW items are compared. */
public enum ProductCondition {
    NEW, USED, REFURBISHED, UNKNOWN;

    /** Parses a condition value from a feed (Polish or English). */
    public static ProductCondition fromFeedValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String v = TextNormalizer.normalize(value);
        return switch (v) {
            case "new", "nowy", "nowe", "nowa" -> NEW;
            case "used", "uzywany", "uzywane", "uzywana" -> USED;
            case "refurbished", "odnowiony", "odnowione", "odnowiona" -> REFURBISHED;
            default -> UNKNOWN;
        };
    }

    /** Detects a used/refurbished item from the offer name, e.g. "[Outlet] ... używany". */
    public static ProductCondition fromName(String name) {
        String n = " " + TextNormalizer.normalize(name) + " ";
        for (String word : new String[]{" uzywany ", " uzywana ", " uzywane ", " outlet ", " powystawowy ",
                " powystawowa ", " used "}) {
            if (n.contains(word)) {
                return USED;
            }
        }
        for (String word : new String[]{" odnowiony ", " odnowiona ", " odnowione ", " refurbished ", " refurb "}) {
            if (n.contains(word)) {
                return REFURBISHED;
            }
        }
        return UNKNOWN;
    }

    /** Combines the feed value with name hints: an explicit used/refurbished signal always wins. */
    public static ProductCondition resolve(String feedValue, String name) {
        ProductCondition fromName = fromName(name);
        if (fromName != UNKNOWN) {
            return fromName;
        }
        ProductCondition fromFeed = fromFeedValue(feedValue);
        return fromFeed == UNKNOWN ? NEW : fromFeed;
    }
}
