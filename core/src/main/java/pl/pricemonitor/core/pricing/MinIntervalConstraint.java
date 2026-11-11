package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Enforces a minimum time between two price changes of the same product. It limits the risk of
 * pricing algorithms of different sellers reacting to each other in a loop.
 */
public class MinIntervalConstraint implements PriceConstraint {

    @Override
    public ConstraintResult apply(BigDecimal proposedPrice, PricingInput input) {
        boolean wouldChange = Money.round(proposedPrice).compareTo(Money.round(input.currentPrice())) != 0;
        Instant last = input.lastChangeAt();
        if (wouldChange && last != null && last.plus(input.rule().minInterval()).isAfter(input.now())) {
            return ConstraintResult.stop(DecisionOutcome.TOO_SOON,
                    "Ostatnia zmiana ceny była mniej niż " + input.rule().minInterval().toMinutes() + " min temu.");
        }
        return ConstraintResult.pass(proposedPrice);
    }
}
