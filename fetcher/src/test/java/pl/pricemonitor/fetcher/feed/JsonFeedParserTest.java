package pl.pricemonitor.fetcher.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.pricemonitor.core.feed.FeedFormat;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.feed.FeedParseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFeedParserTest {

    private final JsonFeedParser parser = new JsonFeedParser(new ObjectMapper());

    private static InputStream text(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSampleFile() {
        List<FeedOffer> offers = parser.parse(getClass().getResourceAsStream("/feeds/sample.json"));

        assertEquals(FeedFormat.JSON, parser.format());
        assertEquals(2, offers.size());
        FeedOffer first = offers.get(0);
        assertEquals("S1-1000", first.shopOfferId());
        assertEquals("Apple", first.brand());
        assertEquals(new BigDecimal("4226.37"), first.price());
        assertEquals("czarny", first.attributes().get("kolor"));
        assertFalse(first.attributes().containsKey("empty"));
        assertTrue(first.available());

        FeedOffer second = offers.get(1);
        assertEquals(new BigDecimal("1549"), second.price());
        assertNull(second.brand());
        assertNull(second.shipping());
        assertFalse(second.available());
    }

    @Test
    void rejectsInvalidFiles() {
        assertThrows(FeedParseException.class, () -> parser.parse(text("{")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("{\"items\": []}")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("{\"offers\": [{\"name\": \"x\", \"price\": 1}]}")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("{\"offers\": [{\"id\": \"1\", \"price\": 1}]}")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("{\"offers\": [{\"id\": \"1\", \"name\": \"x\"}]}")));
    }
}
