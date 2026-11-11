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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import pl.pricemonitor.core.matching.MatchMethod;
import pl.pricemonitor.core.matching.MatchPolicy;
import pl.pricemonitor.core.matching.MatchStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dopasowanie")
public class ProductMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produkt_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "metoda", nullable = false)
    private MatchMethod method;

    @Column(name = "pewnosc", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zweryfikowal_id")
    private AppUser verifiedBy;

    @Column(name = "utworzono", nullable = false)
    private Instant createdAt;

    protected ProductMatch() {
    }

    public ProductMatch(Offer offer, Product product, MatchMethod method, BigDecimal confidence, MatchStatus status) {
        this.offer = offer;
        this.product = product;
        this.method = method;
        this.confidence = confidence;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isReliableForPricing() {
        return MatchPolicy.reliableForPricing(method, status);
    }

    public void verify(MatchStatus newStatus, AppUser reviewer) {
        this.status = newStatus;
        this.verifiedBy = reviewer;
    }

    public Long getId() {
        return id;
    }

    public Offer getOffer() {
        return offer;
    }

    public Product getProduct() {
        return product;
    }

    public MatchMethod getMethod() {
        return method;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public AppUser getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
