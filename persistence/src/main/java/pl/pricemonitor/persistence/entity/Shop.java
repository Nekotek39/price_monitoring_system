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
import pl.pricemonitor.core.feed.FeedFormat;

import java.time.Instant;

@Entity
@Table(name = "sklep")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nazwa", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false)
    private ShopType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wlasciciel_id")
    private AppUser owner;

    /** Address of the price file (monitored shops). */
    @Column(name = "adres_pliku_cen")
    private String feedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_danych")
    private FeedFormat feedFormat;

    /** Base URL of the shop API used to change prices (seller shops). */
    @Column(name = "api_url")
    private String apiUrl;

    /** API key of the seller shop. Stored as plain text in the MVP - encryption is a TODO. */
    @Column(name = "api_klucz_enc")
    private String apiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShopStatus status = ShopStatus.AVAILABLE;

    @Column(name = "niedostepny_do")
    private Instant unavailableUntil;

    @Column(name = "min_odstep_zapytan_s", nullable = false)
    private int minRequestIntervalSeconds = 2;

    protected Shop() {
    }

    public Shop(String name, ShopType type, String feedUrl, FeedFormat feedFormat) {
        this.name = name;
        this.type = type;
        this.feedUrl = feedUrl;
        this.feedFormat = feedFormat;
    }

    public boolean isAvailableAt(Instant now) {
        return status == ShopStatus.AVAILABLE || unavailableUntil == null || !unavailableUntil.isAfter(now);
    }

    public void markUnavailable(Instant until) {
        status = ShopStatus.UNAVAILABLE;
        unavailableUntil = until;
    }

    public void markAvailable() {
        status = ShopStatus.AVAILABLE;
        unavailableUntil = null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ShopType getType() {
        return type;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public FeedFormat getFeedFormat() {
        return feedFormat;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public Instant getUnavailableUntil() {
        return unavailableUntil;
    }

    public int getMinRequestIntervalSeconds() {
        return minRequestIntervalSeconds;
    }
}
