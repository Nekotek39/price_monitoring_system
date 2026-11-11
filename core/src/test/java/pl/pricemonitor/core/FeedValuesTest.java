package pl.pricemonitor.core;

import org.junit.jupiter.api.Test;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.feed.FeedParseException;
import pl.pricemonitor.core.feed.FeedValues;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedValuesTest {

    @Test
    void parsesDecimals() {
        assertEquals(new BigDecimal("1299.00"), FeedValues.decimalOrNull("1 299,00", "price", "X1"));
        assertEquals(new BigDecimal("1299.99"), FeedValues.requiredDecimal("1299.99", "price", "X1"));
        assertNull(FeedValues.decimalOrNull(" ", "shipping", "X1"));
        assertThrows(FeedParseException.class, () -> FeedValues.requiredDecimal(null, "price", "X1"));
        assertThrows(FeedParseException.class, () -> FeedValues.decimalOrNull("abc", "price", "X1"));
        assertThrows(FeedParseException.class, () -> FeedValues.decimalOrNull("-5", "price", "X1"));
    }

    @Test
    void parsesFlagsAndText() {
        assertTrue(FeedValues.flag("1", false));
        assertTrue(FeedValues.flag("TAK", false));
        assertFalse(FeedValues.flag("0", true));
        assertTrue(FeedValues.flag(null, true));
        assertNull(FeedValues.blankToNull("  "));
        assertEquals("abc", FeedValues.blankToNull(" abc "));
    }

    @Test
    void offerRequiresMandatoryFields() {
        assertThrows(NullPointerException.class,
                () -> new FeedOffer(null, "n", null, null, null, null, null, BigDecimal.ONE, null, true, null));
        FeedOffer offer = new FeedOffer("X1", "n", null, null, null, null, null, BigDecimal.ONE, null, true, null);
        assertTrue(offer.attributes().isEmpty());
    }
}
