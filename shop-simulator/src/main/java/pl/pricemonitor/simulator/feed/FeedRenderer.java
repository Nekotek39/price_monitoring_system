package pl.pricemonitor.simulator.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Renders price files. XML follows the format of Polish price comparison feeds ({@code <offers><o ...>}),
 * JSON uses a simple custom structure. Written without libraries so that the output is fully explicit.
 */
public final class FeedRenderer {

    private FeedRenderer() {
    }

    public static String xml(List<FeedItem> items) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<offers>\n");
        for (FeedItem item : items) {
            sb.append("  <o id=\"").append(xmlEscape(item.id())).append('"');
            if (item.url() != null) {
                sb.append(" url=\"").append(xmlEscape(item.url())).append('"');
            }
            sb.append(" price=\"").append(money(item.price())).append('"');
            if (item.shipping() != null) {
                sb.append(" ship=\"").append(money(item.shipping())).append('"');
            }
            sb.append(" avail=\"").append(item.available() ? "1" : "0").append('"');
            sb.append(" stan=\"").append(polishCondition(item.condition())).append("\">\n");
            sb.append("    <name><![CDATA[").append(item.name().replace("]]>", "]]]]><![CDATA[>")).append("]]></name>\n");
            sb.append("    <attrs>\n");
            attr(sb, "Producent", item.brand());
            attr(sb, "EAN", item.gtin());
            attr(sb, "Kod_producenta", item.mpn());
            for (Map.Entry<String, String> e : item.attributes().entrySet()) {
                attr(sb, capitalize(e.getKey()), e.getValue());
            }
            sb.append("    </attrs>\n  </o>\n");
        }
        return sb.append("</offers>\n").toString();
    }

    public static String json(String shopName, Instant generatedAt, List<FeedItem> items) {
        StringBuilder sb = new StringBuilder("{\n  \"shop\": ").append(jsonString(shopName))
                .append(",\n  \"generatedAt\": ").append(jsonString(generatedAt.toString()))
                .append(",\n  \"offers\": [");
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            sb.append(i == 0 ? "\n" : ",\n").append("    {\"id\": ").append(jsonString(item.id()))
                    .append(", \"name\": ").append(jsonString(item.name()))
                    .append(", \"brand\": ").append(jsonString(item.brand()))
                    .append(", \"gtin\": ").append(jsonString(item.gtin()))
                    .append(", \"mpn\": ").append(jsonString(item.mpn()))
                    .append(", \"attributes\": {");
            int k = 0;
            for (Map.Entry<String, String> e : item.attributes().entrySet()) {
                sb.append(k++ == 0 ? "" : ", ").append(jsonString(e.getKey())).append(": ").append(jsonString(e.getValue()));
            }
            sb.append("}, \"condition\": ").append(jsonString(item.condition()))
                    .append(", \"price\": \"").append(money(item.price())).append('"')
                    .append(", \"shipping\": ").append(item.shipping() == null ? "null" : "\"" + money(item.shipping()) + "\"")
                    .append(", \"available\": ").append(item.available())
                    .append(", \"url\": ").append(jsonString(item.url())).append('}');
        }
        return sb.append("\n  ]\n}\n").toString();
    }

    static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void attr(StringBuilder sb, String name, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("      <a name=\"").append(xmlEscape(name)).append("\">").append(xmlEscape(value)).append("</a>\n");
        }
    }

    private static String polishCondition(String condition) {
        if (condition == null) {
            return "nowy";
        }
        return switch (condition) {
            case "used" -> "uzywany";
            case "refurbished" -> "odnowiony";
            default -> "nowy";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
