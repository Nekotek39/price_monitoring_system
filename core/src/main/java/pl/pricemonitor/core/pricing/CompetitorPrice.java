package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Current price of the product in one competitor shop.
 *
 * @param shopId        competitor shop id
 * @param shopName      competitor shop name (used in decision reasons)
 * @param price         offer price
 * @param fetchedAt     when the price was last confirmed by the fetching process
 * @param available     whether the offer is available
 * @param reliableMatch whether the offer-to-product match is reliable enough for automatic decisions
 */
public record CompetitorPrice(long shopId, String shopName, BigDecimal price, Instant fetchedAt,
                              boolean available, boolean reliableMatch) {

    public CompetitorPrice {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }
}
