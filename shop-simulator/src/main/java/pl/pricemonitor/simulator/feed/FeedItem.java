package pl.pricemonitor.simulator.feed;

import java.math.BigDecimal;
import java.util.Map;

/** Current state of one offer as published in a price file. */
public record FeedItem(String id, String name, String brand, String gtin, String mpn, Map<String, String> attributes,
                       String condition, BigDecimal price, BigDecimal shipping, boolean available, String url) {
}
