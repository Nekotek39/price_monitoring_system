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

/** History of decisions of the dynamic pricing module (shown to the seller). */
@Entity
@Table(name = "decyzja_cenowa")
public class PricingDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regula_id", nullable = false)
    private PricingRule rule;

    @Column(name = "cena_poprzednia", nullable = false, precision = 10, scale = 2)
    private BigDecimal previousPrice;

    @Column(name = "cena_nowa", nullable = false, precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "najnizsza_cena_konkurencji", nullable = false, precision = 10, scale = 2)
    private BigDecimal lowestCompetitorPrice;

    @Column(name = "uzasadnienie", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DecisionStatus status;

    @Column(name = "utworzono", nullable = false)
    private Instant createdAt;

    protected PricingDecisionRecord() {
    }

    public PricingDecisionRecord(PricingRule rule, BigDecimal previousPrice, BigDecimal newPrice,
                                 BigDecimal lowestCompetitorPrice, String reason, DecisionStatus status,
                                 Instant createdAt) {
        this.rule = rule;
        this.previousPrice = previousPrice;
        this.newPrice = newPrice;
        this.lowestCompetitorPrice = lowestCompetitorPrice;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PricingRule getRule() {
        return rule;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public BigDecimal getLowestCompetitorPrice() {
        return lowestCompetitorPrice;
    }

    public String getReason() {
        return reason;
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
