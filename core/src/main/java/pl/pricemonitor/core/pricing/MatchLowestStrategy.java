package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Proposes the lowest competitor price. */
public class MatchLowestStrategy implements PricingStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.MATCH_LOWEST;
    }

    @Override
    public BigDecimal propose(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices,
                              Map<String, String> parameters) {
        return Money.round(PricingStrategy.lowest(competitorPrices));
    }
}
