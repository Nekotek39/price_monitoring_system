package pl.pricemonitor.api.pricing;

import java.math.BigDecimal;
import java.time.Instant;

public record DecisionView(Long id, Long ruleId, String productName, BigDecimal previousPrice, BigDecimal newPrice,
                           BigDecimal lowestCompetitorPrice, String reason, String status, Instant createdAt) {
}
