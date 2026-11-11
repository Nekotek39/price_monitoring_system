package pl.pricemonitor.api.product;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final OfferStreamService stream;

    public ProductController(ProductService service, OfferStreamService stream) {
        this.service = service;
        this.stream = stream;
    }

    @Operation(summary = "Wyszukiwanie produktów po nazwie lub kodzie EAN")
    @GetMapping
    public List<ProductSummary> search(@RequestParam(name = "q", required = false) String query,
                                       @RequestParam(defaultValue = "20") int limit) {
        return service.search(query, limit);
    }

    @GetMapping("/{id}")
    public ProductSummary details(@PathVariable Long id) {
        return service.details(id);
    }

    @Operation(summary = "Porównanie cen produktu; nieaktualne dane są odświeżane „na żądanie”")
    @GetMapping("/{id}/offers")
    public OffersResponse offers(@PathVariable Long id) {
        return service.offers(id);
    }

    @Operation(summary = "Strumień SSE z aktualizacjami ofert produktu (zdarzenia: offers, error)")
    @GetMapping(path = "/{id}/offers/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter offersStream(@PathVariable Long id) {
        return stream.subscribe(id, service.snapshot(id));
    }

    @GetMapping("/{id}/history")
    public List<PricePoint> history(@PathVariable Long id, @RequestParam(defaultValue = "7") int days) {
        return service.history(id, days);
    }
}
