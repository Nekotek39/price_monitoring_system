package pl.pricemonitor.core.feed;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * One offer read from a shop price file, in the common model shared by all parsers.
 *
 * @param shopOfferId offer id in the shop (unique within the shop)
 * @param name        offer name
 * @param brand       brand or {@code null}
 * @param gtin        GTIN/EAN or {@code null}
 * @param mpn         manufacturer part number or {@code null}
 * @param attributes  other attributes as provided by the shop (keys not normalized)
 * @param condition   condition as provided by the shop (e.g. "new", "nowy"), may be {@code null}
 * @param price       price
 * @param shipping    shipping cost or {@code null}
 * @param available   availability
 * @param url         offer page address or {@code null}
 */
public record FeedOffer(String shopOfferId, String name, String brand, String gtin, String mpn,
                        Map<String, String> attributes, String condition, BigDecimal price, BigDecimal shipping,
                        boolean available, String url) {

    public FeedOffer {
        Objects.requireNonNull(shopOfferId, "shopOfferId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
