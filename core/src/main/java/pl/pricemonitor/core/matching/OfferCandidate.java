package pl.pricemonitor.core.matching;

import java.util.Map;

/**
 * Offer data used for matching.
 *
 * @param name       offer name in the shop
 * @param brand      brand if the shop provides it, otherwise {@code null}
 * @param gtin       GTIN/EAN or {@code null}
 * @param mpn        manufacturer part number or {@code null}
 * @param attributes raw attributes from the feed (any keys)
 * @param condition  item condition
 */
public record OfferCandidate(String name, String brand, String gtin, String mpn, Map<String, String> attributes,
                             ProductCondition condition) {

    public OfferCandidate {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        condition = condition == null ? ProductCondition.UNKNOWN : condition;
    }
}
