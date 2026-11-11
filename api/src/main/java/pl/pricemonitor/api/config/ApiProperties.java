package pl.pricemonitor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param matcherAcceptThreshold trigram similarity from which a name-based match is accepted
 * @param matcherReviewMargin    pairs this far below the threshold go to manual review
 * @param sseTimeout             lifetime of one SSE connection
 * @param importInterval         how often all offers are re-imported in the dual model (discovers new offers)
 * @param sellerApiTimeout       timeout of calls to the seller shop API
 * @param adminEmail             recipient of error notifications
 * @param mailFrom               sender address of notifications
 */
@ConfigurationProperties(prefix = "pricemonitor.api")
public record ApiProperties(double matcherAcceptThreshold, double matcherReviewMargin, Duration sseTimeout,
                            Duration importInterval, Duration sellerApiTimeout, String adminEmail, String mailFrom) {
}
