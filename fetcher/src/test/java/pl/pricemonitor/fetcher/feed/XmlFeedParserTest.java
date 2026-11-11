package pl.pricemonitor.fetcher.feed;

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

class XmlFeedParserTest {

    private final XmlFeedParser parser = new XmlFeedParser();

    private static InputStream text(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSampleFile() {
        List<FeedOffer> offers = parser.parse(getClass().getResourceAsStream("/feeds/sample.xml"));

        assertEquals(FeedFormat.XML, parser.format());
        assertEquals(2, offers.size());
        FeedOffer first = offers.get(0);
        assertEquals("CS-1000", first.shopOfferId());
        assertEquals("Smartfon Apple iPhone 16 128GB Czarny & etui", first.name());
        assertEquals("Apple", first.brand());
        assertEquals("2000000010007", first.gtin());
        assertEquals("SIM-P01-IPHONE16", first.mpn());
        assertEquals("czarny", first.attributes().get("Kolor"));
        assertEquals("128gb", first.attributes().get("Pamięć"));
        assertFalse(first.attributes().containsKey("Pusty"));
        assertEquals(new BigDecimal("3949.00"), first.price());
        assertEquals(new BigDecimal("9.99"), first.shipping());
        assertTrue(first.available());
        assertEquals("nowy", first.condition());
        assertEquals("https://cyfrowyswiat.example/p/s3-1000", first.url());

        FeedOffer second = offers.get(1);
        assertFalse(second.available());
        assertNull(second.shipping());
        assertNull(second.url());
        assertEquals("odnowiony", second.condition());
    }

    @Test
    void rejectsMissingPrice() {
        assertThrows(FeedParseException.class,
                () -> parser.parse(text("<offers><o id=\"1\"><name>X</name></o></offers>")));
    }

    @Test
    void rejectsMissingIdAndName() {
        assertThrows(FeedParseException.class,
                () -> parser.parse(text("<offers><o price=\"1\"><name>X</name></o></offers>")));
        assertThrows(FeedParseException.class,
                () -> parser.parse(text("<offers><o id=\"1\" price=\"1\"></o></offers>")));
    }

    @Test
    void rejectsMalformedAndWrongDocuments() {
        assertThrows(FeedParseException.class, () -> parser.parse(text("<offers><o id=\"1\"")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("<products></products>")));
        assertThrows(FeedParseException.class, () -> parser.parse(text("")));
    }

    @Test
    void doesNotResolveExternalEntities() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE offers [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<offers><o id=\"1\" price=\"1\"><name>&x;</name></o></offers>";
        assertThrows(FeedParseException.class, () -> parser.parse(text(xxe)));
    }
}
