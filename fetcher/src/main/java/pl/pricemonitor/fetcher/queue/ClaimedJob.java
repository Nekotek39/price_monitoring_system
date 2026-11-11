package pl.pricemonitor.fetcher.queue;

import pl.pricemonitor.persistence.entity.JobType;

import java.time.Instant;

/** Job taken from the queue by this worker. */
public record ClaimedJob(long id, long shopId, Long offerId, Long productId, JobType type, int attempts,
                         Instant startedAt) {
}
