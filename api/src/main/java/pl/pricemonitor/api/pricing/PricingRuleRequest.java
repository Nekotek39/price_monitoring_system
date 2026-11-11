package pl.pricemonitor.api.pricing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.pricemonitor.core.pricing.PricingMode;
import pl.pricemonitor.core.pricing.StrategyType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Seller's rule as sent from the configuration panel.
 *
 * @param parameters        strategy parameters: {@code amount} (UNDERCUT_AMOUNT), {@code percent} (UNDERCUT_PERCENT),
 *                          {@code band} (AVERAGE_BAND)
 * @param competitorShopIds shops treated as direct competitors; empty = all monitored shops
 */
public record PricingRuleRequest(@NotNull Long productId,
                                 @NotBlank String shopProductId,
                                 @NotNull StrategyType strategy,
                                 Map<String, String> parameters,
                                 @NotNull @DecimalMin("0.01") BigDecimal minPrice,
                                 @DecimalMin("0.01") BigDecimal maxPrice,
                                 @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal maxChangePercent,
                                 @Min(0) int minIntervalMinutes,
                                 @NotNull PricingMode mode,
                                 boolean active,
                                 Set<Long> competitorShopIds) {
}
