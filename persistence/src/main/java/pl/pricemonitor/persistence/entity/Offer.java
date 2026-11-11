package pl.pricemonitor.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.pricemonitor.core.matching.ProductCondition;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Offer of a shop. {@code product} is set once the offer is matched to a catalog product. */
@Entity
@Table(name = "oferta")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sklep_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produkt_id")
    private Product product;

    @Column(name = "id_w_sklepie", nullable = false)
    private String shopOfferId;

    @Column(name = "nazwa_w_sklepie", nullable = false)
    private String name;

    @Column(name = "adres_url")
    private String url;

    @Column(name = "gtin")
    private String gtin;

    @Column(name = "mpn")
    private String mpn;

    /** Raw attributes from the feed; the brand is kept under the key "brand". */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atrybuty_wariantu", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> variantAttributes = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "stan_towaru", nullable = false)
    private ProductCondition condition = ProductCondition.UNKNOWN;

    /** Last time the fetching process confirmed this offer (used to compute data age). */
    @Column(name = "ostatnie_pobranie")
    private Instant lastFetchedAt;

    protected Offer() {
    }

    public Offer(Shop shop, String shopOfferId) {
        this.shop = shop;
        this.shopOfferId = shopOfferId;
    }

    public Long getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getShopOfferId() {
        return shopOfferId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.length() > 500 ? name.substring(0, 500) : name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getMpn() {
        return mpn;
    }

    public void setMpn(String mpn) {
        this.mpn = mpn;
    }

    public Map<String, String> getVariantAttributes() {
        return variantAttributes;
    }

    public void setVariantAttributes(Map<String, String> variantAttributes) {
        this.variantAttributes = variantAttributes == null ? new HashMap<>() : new HashMap<>(variantAttributes);
    }

    public String getBrand() {
        return variantAttributes.get("brand");
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public void setCondition(ProductCondition condition) {
        this.condition = condition;
    }

    public Instant getLastFetchedAt() {
        return lastFetchedAt;
    }

    public void setLastFetchedAt(Instant lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }
}
