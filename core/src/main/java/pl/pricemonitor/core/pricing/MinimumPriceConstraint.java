package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;

/**
 * Never goes below the seller's minimum price. If the strategy proposes less, the price is kept
 * and the seller is informed (the seller decides about a possible reduction).
 */
public class MinimumPriceConstraint implements PriceConstraint {

    @Override
    public ConstraintResult apply(BigDecimal proposedPrice, PricingInput input) {
        BigDecimal min = input.rule().minPrice();
        if (proposedPrice.compareTo(min) < 0) {
            return ConstraintResult.stop(DecisionOutcome.KEEP_BELOW_MINIMUM,
                    "Strategia proponuje " + Money.format(proposedPrice) + ", czyli mniej niż cena minimalna "
                            + Money.format(min) + ". Cena pozostaje bez zmian.");
        }
        return ConstraintResult.pass(proposedPrice);
    }
}
