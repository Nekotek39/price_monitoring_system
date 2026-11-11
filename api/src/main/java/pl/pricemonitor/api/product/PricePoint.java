package pl.pricemonitor.api.product;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePoint(Long shopId, String shopName, BigDecimal price, BigDecimal shippingCost, boolean available,
                         String source, Instant fetchedAt) {
}
