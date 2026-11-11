package pl.pricemonitor.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Catalog product (one variant: e.g. a given storage size and color). */
@Entity
@Table(name = "produkt")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nazwa", nullable = false)
    private String name;

    @Column(name = "marka", nullable = false)
    private String brand;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "gtin")
    private String gtin;

    @Column(name = "mpn")
    private String mpn;

    @Column(name = "kategoria", nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atrybuty_wariantu", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> variantAttributes = new HashMap<>();

    @Column(name = "utworzono", nullable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String name, String brand, String model, String category) {
        this.name = name;
        this.brand = brand;
        this.model = model;
        this.category = category;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
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

    public String getCategory() {
        return category;
    }

    public Map<String, String> getVariantAttributes() {
        return variantAttributes;
    }

    public void setVariantAttributes(Map<String, String> variantAttributes) {
        this.variantAttributes = variantAttributes == null ? new HashMap<>() : new HashMap<>(variantAttributes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
