package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Keeps the price within +/- {@code band} percent (default 5) of the average competitor price.
 * A price inside the band is left unchanged; a price outside is moved to the nearest edge of the band.
 */
public class AverageBandStrategy implements PricingStrategy {

    public static final String BAND = "band";

    @Override
    public StrategyType type() {
        return StrategyType.AVERAGE_BAND;
    }

    @Override
    public BigDecimal propose(BigDecimal currentPrice, List<CompetitorPrice> competitorPrices,
                              Map<String, String> parameters) {
        BigDecimal band = StrategyParameters.decimal(parameters, BAND, BigDecimal.valueOf(5));
        BigDecimal sum = competitorPrices.stream().map(CompetitorPrice::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(competitorPrices.size()), 10, RoundingMode.HALF_UP);
        BigDecimal lower = Money.minusPercent(average, band);
        BigDecimal upper = Money.plusPercent(average, band);
        if (currentPrice.compareTo(lower) < 0) {
            return lower;
        }
        if (currentPrice.compareTo(upper) > 0) {
            return upper;
        }
        return Money.round(currentPrice);
    }
}
