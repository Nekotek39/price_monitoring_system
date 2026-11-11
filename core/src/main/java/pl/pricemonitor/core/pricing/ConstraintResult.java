package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;

/**
 * Result of one constraint: either the (possibly adjusted) price goes to the next constraint,
 * or the chain stops with a terminal outcome.
 *
 * @param price   price for the next step (ignored when {@link #stopped()})
 * @param stop    terminal outcome or {@code null}
 * @param note    explanation added to the decision reason, may be {@code null}
 */
public record ConstraintResult(BigDecimal price, DecisionOutcome stop, String note) {

    public static ConstraintResult pass(BigDecimal price) {
        return new ConstraintResult(price, null, null);
    }

    public static ConstraintResult adjusted(BigDecimal price, String note) {
        return new ConstraintResult(price, null, note);
    }

    public static ConstraintResult stop(DecisionOutcome outcome, String note) {
        return new ConstraintResult(null, outcome, note);
    }

    public boolean stopped() {
        return stop != null;
    }
}
