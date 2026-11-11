package pl.pricemonitor.fetcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param maxJobsPerPoll     how many jobs one poll may process before yielding
 * @param maxAttempts        attempts before a job is marked FAILED
 * @param retryBaseDelay     delay before the first retry; doubled with every attempt
 * @param connectTimeout     connection timeout to a shop
 * @param readTimeout        read timeout (one shop must answer within this time)
 * @param shopUnavailableFor how long a shop is skipped after its job failed for good
 * @param staleJobTimeout    IN_PROGRESS jobs older than this are returned to the queue (crashed worker)
 */
@ConfigurationProperties(prefix = "pricemonitor.fetcher")
public record FetcherProperties(int maxJobsPerPoll, int maxAttempts, Duration retryBaseDelay, Duration connectTimeout,
                                Duration readTimeout, Duration shopUnavailableFor, Duration staleJobTimeout) {
}
