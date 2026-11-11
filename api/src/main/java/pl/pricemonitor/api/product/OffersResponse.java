package pl.pricemonitor.api.product;

import java.time.Instant;
import java.util.List;

/**
 * @param popular         product is refreshed cyclically
 * @param refreshing      on-demand fetching is in progress; updates will arrive on the SSE stream
 * @param refreshingShops shops being refreshed
 * @param offers          offers sorted by price (available first)
 */
public record OffersResponse(Long productId, String productName, boolean popular, boolean refreshing,
                             List<String> refreshingShops, List<OfferView> offers, Instant generatedAt) {
}
