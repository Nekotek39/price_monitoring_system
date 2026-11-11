package pl.pricemonitor.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pl.pricemonitor.simulator.feed.FeedItem;
import pl.pricemonitor.simulator.model.SimulatorData;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory state of the simulated shops and of the seller shop. */
@Component
public final class ShopRegistry {

    /** Failure injection for testing error handling of the fetching process. */
    public enum FailureMode {
        NONE, DOWN, SLOW
    }

    /** Mutable offer state. */
    public static final class OfferState {
        private final SimulatorData.OfferData data;
        private volatile BigDecimal price;
        private volatile boolean available;

        OfferState(SimulatorData.OfferData data) {
            this.data = data;
            this.price = data.price();
            this.available = data.available();
        }

        public SimulatorData.OfferData data() {
            return data;
        }

        public BigDecimal price() {
            return price;
        }

        public boolean available() {
            return available;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        FeedItem toFeedItem() {
            return new FeedItem(data.id(), data.name(), data.brand(), data.gtin(), data.mpn(),
                    data.attributes() == null ? Map.of() : data.attributes(), data.condition(), price, data.shipping(),
                    available, data.url());
        }
    }

    /** One simulated shop. */
    public static final class ShopState {
        private final String code;
        private final String name;
        private final String format;
        private final Map<String, OfferState> offers = new LinkedHashMap<>();
        private volatile FailureMode failureMode = FailureMode.NONE;
        private volatile long delayMillis;

        ShopState(SimulatorData.ShopData data) {
            this.code = data.code();
            this.name = data.name();
            this.format = data.format();
            data.offers().forEach(o -> offers.put(o.id(), new OfferState(o)));
        }

        public String code() {
            return code;
        }

        public String name() {
            return name;
        }

        public String format() {
            return format;
        }

        public Map<String, OfferState> offers() {
            return Collections.unmodifiableMap(offers);
        }

        public FailureMode failureMode() {
            return failureMode;
        }

        public long delayMillis() {
            return delayMillis;
        }

        public void setFailure(FailureMode mode, long delayMillis) {
            this.failureMode = mode;
            this.delayMillis = delayMillis;
        }

        public List<FeedItem> feedItems() {
            List<FeedItem> items = new ArrayList<>();
            offers.values().forEach(o -> items.add(o.toFeedItem()));
            return items;
        }
    }

    private final ObjectMapper mapper;
    private final Resource dataFile;
    private final Map<String, ShopState> shops = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> sellerPrices = new ConcurrentHashMap<>();
    private final Map<String, String> sellerNames = new ConcurrentHashMap<>();
    private volatile String sellerApiKey;

    public ShopRegistry(ObjectMapper mapper, @Value("${simulator.data-file}") Resource dataFile) throws IOException {
        this.mapper = mapper;
        this.dataFile = dataFile;
        reset();
    }

    /** Restores the initial prices from the data file. */
    public synchronized void reset() throws IOException {
        SimulatorData data;
        try (InputStream in = dataFile.getInputStream()) {
            data = mapper.readValue(in, SimulatorData.class);
        }
        shops.clear();
        data.shops().forEach(s -> shops.put(s.code(), new ShopState(s)));
        sellerPrices.clear();
        sellerNames.clear();
        data.seller().products().forEach(p -> {
            sellerPrices.put(p.id(), p.price());
            sellerNames.put(p.id(), p.name());
        });
        sellerApiKey = data.seller().apiKey();
    }

    public ShopState shop(String code) {
        ShopState shop = shops.get(code);
        if (shop == null) {
            throw new NoSuchElementException("Unknown shop: " + code);
        }
        return shop;
    }

    public List<ShopState> shops() {
        return shops.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
    }

    public Map<String, BigDecimal> sellerPrices() {
        return Collections.unmodifiableMap(sellerPrices);
    }

    public String sellerName(String productId) {
        return sellerNames.get(productId);
    }

    public BigDecimal sellerPrice(String productId) {
        BigDecimal price = sellerPrices.get(productId);
        if (price == null) {
            throw new NoSuchElementException("Unknown seller product: " + productId);
        }
        return price;
    }

    public void setSellerPrice(String productId, BigDecimal price) {
        sellerPrice(productId);
        sellerPrices.put(productId, price);
    }

    public boolean validSellerKey(String key) {
        return sellerApiKey != null && sellerApiKey.equals(key);
    }
}
