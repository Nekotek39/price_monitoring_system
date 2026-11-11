package pl.pricemonitor.api.matching;

import java.math.BigDecimal;
import java.time.Instant;

public record MatchView(Long id, Long offerId, String offerName, String shopName, Long productId, String productName,
                        String method, BigDecimal confidence, String status, Instant createdAt) {
}
