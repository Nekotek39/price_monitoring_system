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

import java.math.BigDecimal;
import java.time.Instant;

/** Price history entry. A new row is written only when price, shipping cost or availability changes. */
@Entity
@Table(name = "cena")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Offer offer;

    @Column(name = "cena", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "koszt_dostawy", precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "dostepnosc", nullable = false)
    private boolean available;

    @Enumerated(EnumType.STRING)
    @Column(name = "zrodlo", nullable = false)
    private PriceSource source;

    @Column(name = "pobrano", nullable = false)
    private Instant fetchedAt;

    protected PriceRecord() {
    }

    public PriceRecord(Offer offer, BigDecimal price, BigDecimal shippingCost, boolean available, PriceSource source,
                       Instant fetchedAt) {
        this.offer = offer;
        this.price = price;
        this.shippingCost = shippingCost;
        this.available = available;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    /** True when the values differ from the given ones (prices compared numerically). */
    public boolean differsFrom(BigDecimal otherPrice, BigDecimal otherShipping, boolean otherAvailable) {
        boolean shippingEqual = shippingCost == null ? otherShipping == null
                : otherShipping != null && shippingCost.compareTo(otherShipping) == 0;
        return price.compareTo(otherPrice) != 0 || !shippingEqual || available != otherAvailable;
    }

    public Long getId() {
        return id;
    }

    public Offer getOffer() {
        return offer;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public boolean isAvailable() {
        return available;
    }

    public PriceSource getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
