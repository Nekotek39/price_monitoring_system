package pl.pricemonitor.core.matching;

import java.util.Map;

/** Catalog product data used for matching. */
public record ProductCandidate(long id, String name, String brand, String model, String gtin, String mpn,
                               Map<String, String> attributes) {

    public ProductCandidate {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
