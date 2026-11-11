package pl.pricemonitor.simulator.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.pricemonitor.simulator.ShopRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock of the seller's shop API (stands in for e.g. WooCommerce REST API in the tests).
 * Requests must carry the header {@code X-Api-Key}.
 */
@RestController
@RequestMapping("/seller")
public class SellerShopController {

    public record SellerProductView(String id, String name, BigDecimal price) {
    }

    public record PriceUpdate(BigDecimal price) {
    }

    public record PriceChange(Instant at, String productId, BigDecimal oldPrice, BigDecimal newPrice) {
    }

    private final ShopRegistry registry;
    private final List<PriceChange> changes = new CopyOnWriteArrayList<>();

    public SellerShopController(ShopRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/products")
    public List<SellerProductView> products(@RequestHeader("X-Api-Key") String apiKey) {
        authorize(apiKey);
        return registry.sellerPrices().entrySet().stream()
                .map(e -> new SellerProductView(e.getKey(), registry.sellerName(e.getKey()), e.getValue()))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    @GetMapping("/products/{id}")
    public SellerProductView product(@RequestHeader("X-Api-Key") String apiKey, @PathVariable String id) {
        authorize(apiKey);
        return new SellerProductView(id, registry.sellerName(id), registry.sellerPrice(id));
    }

    @PutMapping("/products/{id}/price")
    public SellerProductView updatePrice(@RequestHeader("X-Api-Key") String apiKey, @PathVariable String id,
                                         @RequestBody PriceUpdate update) {
        authorize(apiKey);
        if (update == null || update.price() == null || update.price().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be positive");
        }
        BigDecimal old = registry.sellerPrice(id);
        registry.setSellerPrice(id, update.price());
        changes.add(new PriceChange(Instant.now(), id, old, update.price()));
        return new SellerProductView(id, registry.sellerName(id), update.price());
    }

    @GetMapping("/changes")
    public List<PriceChange> changes(@RequestHeader("X-Api-Key") String apiKey) {
        authorize(apiKey);
        return changes;
    }

    private void authorize(String apiKey) {
        if (!registry.validSellerKey(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid API key");
        }
    }
}
