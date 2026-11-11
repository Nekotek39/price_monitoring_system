package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Proposes the lowest competitor price minus a fixed amount (parameter {@code amount}, default 1.00). */
public class UndercutByAmountStrategy implements PricingStrategy {

    public static final String AMOUNT = "amount";

    @Override
    public StrategyType type() {
        return StrategyType.UNDERCUT_AMOUNT;
    }

    @Override
    public BigDecimal propose(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices,
                              Map<String, String> parameters) {
        BigDecimal amount = StrategyParameters.decimal(parameters, AMOUNT, BigDecimal.ONE);
        return Money.round(PricingStrategy.lowest(competitorPrices).subtract(amount));
    }
}
