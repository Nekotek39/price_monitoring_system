package pl.pricemonitor.api.pricing;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.pricemonitor.api.config.ApiProperties;
import pl.pricemonitor.persistence.entity.Shop;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client of the seller shop API (in tests: the mock in shop-simulator). A WooCommerce adapter would
 * implement the same two operations with {@code GET/PUT /wp-json/wc/v3/products/{id}}.
 */
@Component
public class SellerShopClient {

    public record SellerProduct(String id, String name, BigDecimal price) {
    }

    private final RestClient restClient;

    public SellerShopClient(RestClient.Builder builder, ApiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.sellerApiTimeout());
        factory.setReadTimeout(properties.sellerApiTimeout());
        this.restClient = builder.requestFactory(factory).build();
    }

    /** @throws org.springframework.web.client.RestClientException when the shop API is not available */
    public BigDecimal currentPrice(Shop shop, String productId) {
        SellerProduct product = restClient.get()
                .uri(shop.getApiUrl() + "/products/{id}", productId)
                .header("X-Api-Key", shop.getApiKey())
                .retrieve()
                .body(SellerProduct.class);
        if (product == null || product.price() == null) {
            throw new IllegalStateException("Seller shop returned no price for product " + productId);
        }
        return product.price();
    }

    public void updatePrice(Shop shop, String productId, BigDecimal price) {
        restClient.put()
                .uri(shop.getApiUrl() + "/products/{id}/price", productId)
                .header("X-Api-Key", shop.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", price))
                .retrieve()
                .toBodilessEntity();
    }
}
