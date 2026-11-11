package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;

/** Caps the proposal at the optional maximum price. */
public class MaximumPriceConstraint implements PriceConstraint {

    @Override
    public ConstraintResult apply(BigDecimal proposedPrice, PricingInput input) {
        BigDecimal max = input.rule().maxPrice();
        if (max != null && proposedPrice.compareTo(max) > 0) {
            return ConstraintResult.adjusted(max, "Ograniczono do ceny maksymalnej " + Money.format(max) + ".");
        }
        return ConstraintResult.pass(proposedPrice);
    }
}
