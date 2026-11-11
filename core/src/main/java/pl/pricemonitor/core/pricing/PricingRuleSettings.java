package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Seller's configuration for one product.
 *
 * @param strategy         chosen strategy
 * @param parameters       strategy parameters, e.g. {@code amount}, {@code percent}, {@code band}
 * @param minPrice         price below which selling is not profitable (always respected)
 * @param maxPrice         optional upper limit, may be {@code null}
 * @param maxChangePercent maximum single change relative to the current price, in percent
 * @param minInterval      minimum time between two price changes of the product
 * @param maxDataAge       competitor prices older than this are considered stale
 */
public record PricingRuleSettings(StrategyType strategy, Map<String, String> parameters, BigDecimal minPrice,
                                  BigDecimal maxPrice, BigDecimal maxChangePercent, Duration minInterval,
                                  Duration maxDataAge) {

    public PricingRuleSettings {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(minPrice, "minPrice");
        Objects.requireNonNull(maxChangePercent, "maxChangePercent");
        Objects.requireNonNull(minInterval, "minInterval");
        Objects.requireNonNull(maxDataAge, "maxDataAge");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (maxPrice != null && maxPrice.compareTo(minPrice) < 0) {
            throw new IllegalArgumentException("maxPrice must not be lower than minPrice");
        }
    }
}
