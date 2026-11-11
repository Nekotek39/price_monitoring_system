package pl.pricemonitor.core.pricing;

/** How a pricing decision is executed. */
public enum PricingMode {
    /** The new price is set in the seller's shop through its API and the seller gets an e-mail about it. */
    AUTOMATIC,
    /** The seller only gets an e-mail with the recommended price. */
    NOTIFY
}
