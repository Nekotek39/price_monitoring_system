package pl.pricemonitor.simulator.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.pricemonitor.simulator.ShopRegistry;
import pl.pricemonitor.simulator.feed.FeedRenderer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Price files of the simulated shops: GET /shops/{code}/feed (XML or JSON depending on the shop). */
@RestController
public class FeedController {

    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final ShopRegistry registry;

    public FeedController(ShopRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/shops/{code}/feed")
    public ResponseEntity<String> feed(@PathVariable String code) throws InterruptedException {
        ShopRegistry.ShopState shop = registry.shop(code);
        switch (shop.failureMode()) {
            case DOWN -> {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Sklep chwilowo niedostępny");
            }
            case SLOW -> Thread.sleep(shop.delayMillis());
            case NONE -> {
                // normal response
            }
        }
        if ("XML".equalsIgnoreCase(shop.format())) {
            return ResponseEntity.ok().contentType(XML_UTF8).body(FeedRenderer.xml(shop.feedItems()));
        }
        return ResponseEntity.ok().contentType(JSON_UTF8)
                .body(FeedRenderer.json(shop.name(), Instant.now(), shop.feedItems()));
    }
}
