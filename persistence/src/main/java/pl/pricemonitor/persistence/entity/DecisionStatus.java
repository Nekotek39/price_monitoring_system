package pl.pricemonitor.persistence.entity;

/** APPLIED = price changed in the seller shop, SENT = recommendation e-mailed, KEPT = price kept (competitor below minimum), FAILED = error. */
public enum DecisionStatus {
    APPLIED, SENT, KEPT, FAILED
}
