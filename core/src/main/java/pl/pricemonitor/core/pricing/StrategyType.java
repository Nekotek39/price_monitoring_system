package pl.pricemonitor.core.pricing;

/** Pricing strategies a seller can choose for a product. */
public enum StrategyType {
    /** Match the lowest competitor price. */
    MATCH_LOWEST,
    /** Lowest competitor price minus a fixed amount (parameter {@code amount}). */
    UNDERCUT_AMOUNT,
    /** Lowest competitor price minus a percentage (parameter {@code percent}). */
    UNDERCUT_PERCENT,
    /** Keep the price within +/- {@code band} percent of the average competitor price. */
    AVERAGE_BAND
}
