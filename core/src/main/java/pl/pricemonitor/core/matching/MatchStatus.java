package pl.pricemonitor.core.matching;

/** Lifecycle of a match. */
public enum MatchStatus {
    /** Accepted automatically. */
    AUTOMATIC,
    /** Close to the threshold or incomplete attributes: waits for manual verification. */
    TO_REVIEW,
    /** Confirmed by a person. */
    APPROVED,
    /** Rejected by a person. */
    REJECTED
}
