package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Strategy design pattern: each implementation proposes a price from competitor prices.
 * Adding a strategy means adding a class, without changing the engine.
 */
public interface PricingStrategy {

    StrategyType type();

    /**
     * @param currentPrice     seller's current price
     * @param competitorPrices reliable, available competitor prices (never empty)
     * @param parameters       strategy parameters from the seller's rule
     * @return proposed price before constraints
     */
    BigDecimal propose(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices, Map<String, String> parameters);

    static BigDecimal lowest(List<CompetitorPrice> prices) {
        return prices.stream().map(CompetitorPrice::price).min(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("No competitor prices"));
    }
}
