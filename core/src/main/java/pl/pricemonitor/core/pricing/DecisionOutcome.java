package pl.pricemonitor.core.pricing;

/** Result type of a pricing decision. */
public enum DecisionOutcome {
    /** The price should be changed to {@link PricingDecision#finalPrice()}. */
    CHANGE,
    /** The proposal equals the current price. */
    NO_CHANGE,
    /** A competitor sells below the seller's minimum price: the price stays, the seller is informed. */
    KEEP_BELOW_MINIMUM,
    /** The last change was too recent (minimum interval between changes). */
    TOO_SOON,
    /** Some competitor prices are too old; fresh data must be fetched before deciding. */
    STALE_DATA,
    /** No reliable competitor prices are available. */
    NO_DATA
}
