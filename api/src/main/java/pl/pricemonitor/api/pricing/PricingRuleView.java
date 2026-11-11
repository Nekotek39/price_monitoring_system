package pl.pricemonitor.api.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PricingRuleView(Long id, Long productId, String productName, String shopProductId, String strategy,
                              Map<String, String> parameters, BigDecimal minPrice, BigDecimal maxPrice,
                              BigDecimal maxChangePercent, int minIntervalMinutes, String mode, boolean active,
                              List<Long> competitorShopIds, List<String> competitorShopNames) {
}
