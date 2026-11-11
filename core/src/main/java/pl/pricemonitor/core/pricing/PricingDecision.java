package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of the pricing engine.
 *
 * @param outcome               decision type
 * @param currentPrice          seller's price before the decision
 * @param proposedPrice         price proposed by the strategy before constraints, {@code null} if not computed
 * @param finalPrice            price after all constraints (equals current price unless outcome is CHANGE)
 * @param lowestCompetitorPrice lowest reliable competitor price, {@code null} if there is none
 * @param staleShopIds          shops whose data is too old (only for STALE_DATA)
 * @param reason                human-readable explanation (Polish, shown to the seller)
 */
public record PricingDecision(DecisionOutcome outcome, BigDecimal currentPrice, BigDecimal proposedPrice,
                              BigDecimal finalPrice, BigDecimal lowestCompetitorPrice, List<Long> staleShopIds,
                              String reason) {

    public PricingDecision {
        staleShopIds = staleShopIds == null ? List.of() : List.copyOf(staleShopIds);
    }

    public boolean changesPrice() {
        return outcome == DecisionOutcome.CHANGE;
    }
}
