package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;

/** Limits a single change to {@code maxChangePercent} of the current price. */
public class MaxChangeConstraint implements PriceConstraint {

    @Override
    public ConstraintResult apply(BigDecimal proposedPrice, PricingInput input) {
        BigDecimal current = input.currentPrice();
        BigDecimal percent = input.rule().maxChangePercent();
        BigDecimal lower = Money.minusPercent(current, percent);
        BigDecimal upper = Money.plusPercent(current, percent);
        if (proposedPrice.compareTo(lower) < 0) {
            return ConstraintResult.adjusted(lower, "Ograniczono jednorazową obniżkę do " + percent.stripTrailingZeros()
                    .toPlainString() + "% (" + Money.format(lower) + ").");
        }
        if (proposedPrice.compareTo(upper) > 0) {
            return ConstraintResult.adjusted(upper, "Ograniczono jednorazową podwyżkę do " + percent.stripTrailingZeros()
                    .toPlainString() + "% (" + Money.format(upper) + ").");
        }
        return ConstraintResult.pass(proposedPrice);
    }
}
