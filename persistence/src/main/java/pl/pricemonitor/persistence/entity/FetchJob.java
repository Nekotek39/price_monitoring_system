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

import java.time.Instant;

/**
 * Job in the queue table between the main application and the fetching process.
 * The fetching process claims jobs with SQL (SELECT ... FOR UPDATE SKIP LOCKED), see the fetcher module.
 */
@Entity
@Table(name = "zadanie_pobrania")
public class FetchJob {

    /** Priority of jobs triggered by a user waiting for the result. */
    public static final int PRIORITY_ON_DEMAND = 10;
    public static final int PRIORITY_CYCLIC = 5;
    public static final int PRIORITY_IMPORT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sklep_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_id")
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produkt_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false)
    private JobType type;

    @Column(name = "priorytet", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "liczba_prob", nullable = false)
    private int attempts;

    @Column(name = "blad")
    private String error;

    @Column(name = "utworzono", nullable = false)
    private Instant createdAt;

    @Column(name = "nie_wczesniej_niz", nullable = false)
    private Instant notBefore;

    @Column(name = "rozpoczeto")
    private Instant startedAt;

    @Column(name = "zakonczono")
    private Instant finishedAt;

    protected FetchJob() {
    }

    private FetchJob(Shop shop, Product product, JobType type, int priority, Instant now) {
        this.shop = shop;
        this.product = product;
        this.type = type;
        this.priority = priority;
        this.createdAt = now;
        this.notBefore = now;
    }

    public static FetchJob importJob(Shop shop, Instant now) {
        return new FetchJob(shop, null, JobType.IMPORT, PRIORITY_IMPORT, now);
    }

    public static FetchJob cyclic(Shop shop, Instant now) {
        return new FetchJob(shop, null, JobType.CYCLIC, PRIORITY_CYCLIC, now);
    }

    public static FetchJob onDemand(Shop shop, Product product, Instant now) {
        return new FetchJob(shop, product, JobType.ON_DEMAND, PRIORITY_ON_DEMAND, now);
    }

    public Long getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public Offer getOffer() {
        return offer;
    }

    public Product getProduct() {
        return product;
    }

    public JobType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
