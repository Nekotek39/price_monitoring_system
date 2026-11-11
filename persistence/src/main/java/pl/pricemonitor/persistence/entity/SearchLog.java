package pl.pricemonitor.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One search or product view; the number of entries per product decides its popularity. */
@Entity
@Table(name = "wyszukiwanie")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produkt_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uzytkownik_id")
    private AppUser user;

    @Column(name = "fraza", nullable = false)
    private String phrase;

    @Column(name = "utworzono", nullable = false)
    private Instant createdAt;

    protected SearchLog() {
    }

    public SearchLog(Product product, AppUser user, String phrase, Instant createdAt) {
        this.product = product;
        this.user = user;
        this.phrase = phrase.length() > 255 ? phrase.substring(0, 255) : phrase;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getPhrase() {
        return phrase;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
