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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.pricemonitor.core.pricing.PricingMode;
import pl.pricemonitor.core.pricing.PricingRuleSettings;
import pl.pricemonitor.core.pricing.StrategyType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Seller's pricing configuration for one product. */
@Entity
@Table(name = "regula_cenowa")
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uzytkownik_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produkt_id", nullable = false)
    private Product product;

    /** Product id in the seller's shop (used when calling the shop API). */
    @Column(name = "id_produktu_w_sklepie", nullable = false)
    private String shopProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategia", nullable = false)
    private StrategyType strategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parametry", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> parameters = new HashMap<>();

    @Column(name = "cena_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "cena_max", precision = 10, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "max_zmiana_proc", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxChangePercent;

    @Column(name = "min_odstep_min", nullable = false)
    private int minIntervalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "tryb", nullable = false)
    private PricingMode mode;

    @Column(name = "aktywna", nullable = false)
    private boolean active = true;

    /** Shops treated as direct competitors; empty set means all monitored shops. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "regula_konkurent",
            joinColumns = @JoinColumn(name = "regula_id"),
            inverseJoinColumns = @JoinColumn(name = "sklep_id"))
    private Set<Shop> competitors = new HashSet<>();

    protected PricingRule() {
    }

    public PricingRule(AppUser owner, Product product, String shopProductId) {
        this.owner = owner;
        this.product = product;
        this.shopProductId = shopProductId;
    }

    /** Converts the stored configuration into the pricing engine model. */
    public PricingRuleSettings toSettings(Duration maxDataAge) {
        return new PricingRuleSettings(strategy, parameters, minPrice, maxPrice, maxChangePercent,
                Duration.ofMinutes(minIntervalMinutes), maxDataAge);
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public Product getProduct() {
        return product;
    }

    public String getShopProductId() {
        return shopProductId;
    }

    public void setShopProductId(String shopProductId) {
        this.shopProductId = shopProductId;
    }

    public StrategyType getStrategy() {
        return strategy;
    }

    public void setStrategy(StrategyType strategy) {
        this.strategy = strategy;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public BigDecimal getMaxChangePercent() {
        return maxChangePercent;
    }

    public void setMaxChangePercent(BigDecimal maxChangePercent) {
        this.maxChangePercent = maxChangePercent;
    }

    public int getMinIntervalMinutes() {
        return minIntervalMinutes;
    }

    public void setMinIntervalMinutes(int minIntervalMinutes) {
        this.minIntervalMinutes = minIntervalMinutes;
    }

    public PricingMode getMode() {
        return mode;
    }

    public void setMode(PricingMode mode) {
        this.mode = mode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Shop> getCompetitors() {
        return competitors;
    }

    public void setCompetitors(Set<Shop> competitors) {
        this.competitors.clear();
        if (competitors != null) {
            this.competitors.addAll(competitors);
        }
    }
}
