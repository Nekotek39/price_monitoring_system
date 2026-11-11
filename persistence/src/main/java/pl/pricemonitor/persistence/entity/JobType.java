package pl.pricemonitor.persistence.entity;

/** IMPORT = all offers of a shop (also new ones), CYCLIC = known offers of popular products, ON_DEMAND = offers of one product. */
public enum JobType {
    IMPORT, CYCLIC, ON_DEMAND
}
