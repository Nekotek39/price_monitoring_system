package pl.pricemonitor.simulator;

import org.junit.jupiter.api.Test;
import pl.pricemonitor.simulator.feed.FeedItem;
import pl.pricemonitor.simulator.feed.FeedRenderer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedRendererTest {

    private static FeedItem item() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("kolor", "czarny");
        return new FeedItem("S1-1", "Słuchawki \"Pro\" <2026> & ]]> test", "Sony", "2000000010007", null, attrs, "used",
                new BigDecimal("1549.5"), null, false, "https://x.example/p?a=1&b=2");
    }

    @Test
    void rendersXml() {
        String xml = FeedRenderer.xml(List.of(item()));

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<o id=\"S1-1\" url=\"https://x.example/p?a=1&amp;b=2\" price=\"1549.50\" avail=\"0\" stan=\"uzywany\">"));
        assertTrue(xml.contains("<a name=\"Producent\">Sony</a>"));
        assertTrue(xml.contains("<a name=\"Kolor\">czarny</a>"));
        assertTrue(xml.contains("]]]]><![CDATA[>"));
    }

    @Test
    void rendersJson() {
        String json = FeedRenderer.json("Sklep \"A\"", Instant.parse("2026-11-11T10:00:00Z"), List.of(item(), item()));

        assertTrue(json.contains("\"shop\": \"Sklep \\\"A\\\"\""));
        assertTrue(json.contains("\"price\": \"1549.50\""));
        assertTrue(json.contains("\"shipping\": null"));
        assertTrue(json.contains("\"mpn\": null"));
        assertTrue(json.contains("\"attributes\": {\"kolor\": \"czarny\"}"));
        assertTrue(json.contains("\"available\": false"));
    }
}
