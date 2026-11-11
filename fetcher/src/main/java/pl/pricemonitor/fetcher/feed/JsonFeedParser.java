package pl.pricemonitor.fetcher.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.pricemonitor.core.feed.FeedFormat;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.feed.FeedParseException;
import pl.pricemonitor.core.feed.FeedParser;
import pl.pricemonitor.core.feed.FeedValues;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser of JSON price files:
 * <pre>{@code
 * {"shop": "ElektroMax", "generatedAt": "...", "offers": [
 *   {"id": "S1-1000", "name": "...", "brand": "Apple", "gtin": "...", "mpn": "...",
 *    "attributes": {"kolor": "czarny"}, "condition": "new", "price": "3999.00", "shipping": "0.00",
 *    "available": true, "url": "..."}]}
 * }</pre>
 */
@Component
public class JsonFeedParser implements FeedParser {

    private final ObjectMapper mapper;

    public JsonFeedParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FeedFormat format() {
        return FeedFormat.JSON;
    }

    @Override
    public List<FeedOffer> parse(InputStream input) {
        JsonNode root;
        try {
            root = mapper.readTree(input);
        } catch (IOException e) {
            throw new FeedParseException("Malformed JSON price file: " + e.getMessage(), e);
        }
        if (root == null || !root.path("offers").isArray()) {
            throw new FeedParseException("JSON price file must contain an \"offers\" array");
        }
        List<FeedOffer> offers = new ArrayList<>();
        for (JsonNode node : root.path("offers")) {
            String id = text(node, "id");
            if (id == null) {
                throw new FeedParseException("Offer without id");
            }
            String name = text(node, "name");
            if (name == null) {
                throw new FeedParseException("Missing name in offer " + id);
            }
            BigDecimal price = FeedValues.requiredDecimal(text(node, "price"), "price", id);
            BigDecimal shipping = FeedValues.decimalOrNull(text(node, "shipping"), "shipping", id);
            boolean available = FeedValues.flag(text(node, "available"), true);
            Map<String, String> attributes = new LinkedHashMap<>();
            JsonNode attrs = node.path("attributes");
            if (attrs.isObject()) {
                for (Map.Entry<String, JsonNode> e : attrs.properties()) {
                    if (!e.getValue().isNull()) {
                        attributes.put(e.getKey(), e.getValue().asText());
                    }
                }
            }
            offers.add(new FeedOffer(id, name, text(node, "brand"), text(node, "gtin"), text(node, "mpn"), attributes,
                    text(node, "condition"), price, shipping, available, text(node, "url")));
        }
        return offers;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return FeedValues.blankToNull(value.asText());
    }
}
