package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything the pricing engine needs to make one decision.
 *
 * @param currentPrice     seller's current price
 * @param competitorPrices prices of the product in competitor shops
 * @param rule             seller's rule
 * @param now              decision time
 * @param lastChangeAt     time of the last applied price change, {@code null} if none
 */
public record PricingInput(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices, PricingRuleSettings rule,
                           Instant now, Instant lastChangeAt) {

    public PricingInput {
        Objects.requireNonNull(currentPrice, "currentPrice");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(now, "now");
        competitorPrices = competitorPrices == null ? List.of() : List.copyOf(competitorPrices);
    }
}
