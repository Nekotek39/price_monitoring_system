package pl.pricemonitor.core.matching;

/** Rules for using matches. */
public final class MatchPolicy {

    private MatchPolicy() {
    }

    /**
     * Only matches confirmed by an identifier (with equal variant attributes) or approved by a person
     * may trigger an automatic pricing decision.
     */
    public static boolean reliableForPricing(MatchMethod method, MatchStatus status) {
        return status == MatchStatus.APPROVED
                || (status == MatchStatus.AUTOMATIC && method == MatchMethod.IDENTIFIER);
    }

    /** Matches visible in the price comparison (name-based ones are shown with a lower-confidence flag). */
    public static boolean visibleInComparison(MatchStatus status) {
        return status == MatchStatus.AUTOMATIC || status == MatchStatus.APPROVED;
    }
}
