package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;

/**
 * One step of checking the proposed price. Constraints always take precedence over the strategy
 * and are applied in a fixed order (chain of responsibility).
 */
public interface PriceConstraint {

    ConstraintResult apply(BigDecimal proposedPrice, PricingInput input);
}
