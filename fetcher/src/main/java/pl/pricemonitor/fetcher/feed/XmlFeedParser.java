package pl.pricemonitor.fetcher.feed;

import org.springframework.stereotype.Component;
import pl.pricemonitor.core.feed.FeedFormat;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.feed.FeedParseException;
import pl.pricemonitor.core.feed.FeedParser;
import pl.pricemonitor.core.feed.FeedValues;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser of XML price files in a format similar to the one used by Polish price comparison sites:
 * <pre>{@code
 * <offers>
 *   <o id="CS-1001" url="..." price="3949.00" ship="9.99" avail="1" stan="nowy">
 *     <name>Smartfon Apple iPhone 16 128GB Czarny</name>
 *     <attrs><a name="Producent">Apple</a><a name="EAN">...</a><a name="Kod_producenta">...</a><a name="Kolor">czarny</a></attrs>
 *   </o>
 * </offers>
 * }</pre>
 * Optional {@code <group>} elements are ignored. Uses a streaming parser (StAX) with DTDs and external
 * entities disabled.
 */
@Component
public class XmlFeedParser implements FeedParser {

    private final XMLInputFactory factory;

    public XmlFeedParser() {
        factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    @Override
    public FeedFormat format() {
        return FeedFormat.XML;
    }

    @Override
    public List<FeedOffer> parse(InputStream input) {
        List<FeedOffer> offers = new ArrayList<>();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(input);
            boolean rootSeen = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String element = reader.getLocalName();
                if (!rootSeen) {
                    if (!"offers".equals(element)) {
                        throw new FeedParseException("Unexpected root element <" + element + ">, expected <offers>");
                    }
                    rootSeen = true;
                } else if ("o".equals(element)) {
                    offers.add(readOffer(reader));
                }
            }
            if (!rootSeen) {
                throw new FeedParseException("Empty XML document");
            }
            return offers;
        } catch (XMLStreamException e) {
            throw new FeedParseException("Malformed XML price file: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // nothing to do
                }
            }
        }
    }

    private FeedOffer readOffer(XMLStreamReader reader) throws XMLStreamException {
        String id = FeedValues.blankToNull(reader.getAttributeValue(null, "id"));
        if (id == null) {
            throw new FeedParseException("Offer without id at line " + reader.getLocation().getLineNumber());
        }
        BigDecimal price = FeedValues.requiredDecimal(reader.getAttributeValue(null, "price"), "price", id);
        BigDecimal shipping = FeedValues.decimalOrNull(reader.getAttributeValue(null, "ship"), "shipping", id);
        boolean available = FeedValues.flag(reader.getAttributeValue(null, "avail"), true);
        String url = FeedValues.blankToNull(reader.getAttributeValue(null, "url"));
        String condition = FeedValues.blankToNull(reader.getAttributeValue(null, "stan"));

        String name = null;
        String brand = null;
        String gtin = null;
        String mpn = null;
        Map<String, String> attributes = new LinkedHashMap<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "o".equals(reader.getLocalName())) {
                break;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "name" -> name = FeedValues.blankToNull(reader.getElementText());
                case "a" -> {
                    String attrName = reader.getAttributeValue(null, "name");
                    String value = FeedValues.blankToNull(reader.getElementText());
                    if (attrName == null || value == null) {
                        continue;
                    }
                    switch (key(attrName)) {
                        case "producent", "marka", "brand" -> brand = value;
                        case "ean", "gtin" -> gtin = value;
                        case "kod_producenta", "kod producenta", "mpn" -> mpn = value;
                        default -> attributes.put(attrName, value);
                    }
                }
                default -> {
                    // other elements (e.g. <cat>, <desc>, <imgs>) are not needed
                }
            }
        }
        if (name == null) {
            throw new FeedParseException("Missing <name> in offer " + id);
        }
        return new FeedOffer(id, name, brand, gtin, mpn, attributes, condition, price, shipping, available, url);
    }

    private static String key(String attrName) {
        String s = Normalizer.normalize(attrName.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "");
    }
}
