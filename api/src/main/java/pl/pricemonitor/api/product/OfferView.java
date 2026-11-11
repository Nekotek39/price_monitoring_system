package pl.pricemonitor.api.product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One offer in the price comparison.
 *
 * @param ageSeconds    how old the price is (time since the fetching process last confirmed it)
 * @param lowConfidence the match is based on names/attributes only (shown with a marker in the UI)
 */
public record OfferView(Long offerId, Long shopId, String shopName, String name, BigDecimal price,
                        BigDecimal shippingCost, boolean available, String url, Instant fetchedAt, Long ageSeconds,
                        String matchMethod, BigDecimal matchConfidence, boolean lowConfidence) {
}
