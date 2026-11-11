package pl.pricemonitor.persistence.entity;

/** Which path of the dual model produced a price record. */
public enum PriceSource {
    IMPORT, CYCLIC, ON_DEMAND
}
