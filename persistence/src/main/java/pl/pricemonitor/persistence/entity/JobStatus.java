package pl.pricemonitor.persistence.entity;

/** State of a fetch job in the queue. */
public enum JobStatus {
    PENDING, IN_PROGRESS, DONE, FAILED
}
