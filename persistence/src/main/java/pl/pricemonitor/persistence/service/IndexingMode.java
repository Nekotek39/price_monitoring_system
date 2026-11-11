package pl.pricemonitor.persistence.service;

/** Data collection model; switching it allows comparing both models in the same test scenario. */
public enum IndexingMode {
    /** Cyclic indexing of popular products + on-demand fetching for the rest (the proposed model). */
    DUAL,
    /** Baseline: cyclic refresh of all offers, no on-demand fetching. */
    CYCLIC_ONLY
}
