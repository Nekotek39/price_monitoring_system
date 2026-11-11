package pl.pricemonitor.api.product;

import java.math.BigDecimal;
import java.util.Map;

public record ProductSummary(Long id, String name, String brand, String model, String category, String gtin,
                             Map<String, String> variantAttributes, BigDecimal lowestPrice, int offerCount) {
}
