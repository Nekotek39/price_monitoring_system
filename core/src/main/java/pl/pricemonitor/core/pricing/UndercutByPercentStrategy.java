package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Proposes the lowest competitor price minus a percentage (parameter {@code percent}, default 1). */
public class UndercutByPercentStrategy implements PricingStrategy {

    public static final String PERCENT = "percent";

    @Override
    public StrategyType type() {
        return StrategyType.UNDERCUT_PERCENT;
    }

    @Override
    public BigDecimal propose(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices,
                              Map<String, String> parameters) {
        BigDecimal percent = StrategyParameters.decimal(parameters, PERCENT, BigDecimal.ONE);
        return Money.minusPercent(PricingStrategy.lowest(competitorPrices), percent);
    }
}
