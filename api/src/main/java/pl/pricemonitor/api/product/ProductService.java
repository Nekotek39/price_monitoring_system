package pl.pricemonitor.api.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.api.jobs.FetchJobScheduler;
import pl.pricemonitor.api.web.NotFoundException;
import pl.pricemonitor.core.matching.MatchMethod;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.PriceRecord;
import pl.pricemonitor.persistence.entity.Product;
import pl.pricemonitor.persistence.entity.ProductMatch;
import pl.pricemonitor.persistence.entity.SearchLog;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.repository.OfferRepository;
import pl.pricemonitor.persistence.repository.PriceRecordRepository;
import pl.pricemonitor.persistence.repository.ProductMatchRepository;
import pl.pricemonitor.persistence.repository.ProductRepository;
import pl.pricemonitor.persistence.repository.SearchLogRepository;
import pl.pricemonitor.persistence.service.IndexingMode;
import pl.pricemonitor.persistence.service.PopularityService;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Product search, price comparison (with the on-demand path) and price history. */
@Service
public class ProductService {

    private final ProductRepository products;
    private final OfferRepository offers;
    private final PriceRecordRepository prices;
    private final ProductMatchRepository matches;
    private final SearchLogRepository searchLogs;
    private final PopularityService popularity;
    private final SystemSettings settings;
    private final FetchJobScheduler jobScheduler;
    private final Clock clock;

    public ProductService(ProductRepository products, OfferRepository offers, PriceRecordRepository prices,
                          ProductMatchRepository matches, SearchLogRepository searchLogs, PopularityService popularity,
                          SystemSettings settings, FetchJobScheduler jobScheduler, Clock clock) {
        this.products = products;
        this.offers = offers;
        this.prices = prices;
        this.matches = matches;
        this.searchLogs = searchLogs;
        this.popularity = popularity;
        this.settings = settings;
        this.jobScheduler = jobScheduler;
        this.clock = clock;
    }

    @Transactional
    public List<ProductSummary> search(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Product> found;
        if (query == null || query.isBlank()) {
            found = products.findAll().stream().sorted(Comparator.comparing(Product::getName)).limit(safeLimit).toList();
        } else {
            String q = query.trim();
            found = products.search(q, safeLimit);
            searchLogs.save(new SearchLog(null, null, q, clock.instant()));
        }
        return found.stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ProductSummary details(Long productId) {
        return summary(product(productId));
    }

    /**
     * Price comparison of a product. Counts as a product view (popularity) and, in the dual model, starts
     * on-demand fetching for shops whose data is too old. Fresh data is then pushed through SSE.
     */
    @Transactional
    public OffersResponse offers(Long productId) {
        Product product = product(productId);
        searchLogs.save(new SearchLog(product, null, product.getName(), clock.instant()));
        List<Offer> productOffers = offers.findByProduct_Id(productId);
        List<String> refreshing = settings.indexingMode() == IndexingMode.DUAL
                ? jobScheduler.refreshIfStale(product, productOffers)
                : List.of();
        return response(product, productOffers, refreshing);
    }

    /** Current state without side effects (used for SSE updates). */
    @Transactional(readOnly = true)
    public OffersResponse snapshot(Long productId) {
        Product product = product(productId);
        List<Offer> productOffers = offers.findByProduct_Id(productId);
        List<String> refreshing = productOffers.stream().map(Offer::getShop).distinct()
                .filter(shop -> jobScheduler.hasActiveJobs(shop.getId(), productId))
                .map(Shop::getName).toList();
        return response(product, productOffers, refreshing);
    }

    @Transactional(readOnly = true)
    public List<PricePoint> history(Long productId, int days) {
        product(productId);
        Instant since = clock.instant().minus(Duration.ofDays(Math.max(1, days)));
        return prices.findByOffer_Product_IdAndFetchedAtAfterOrderByFetchedAtAsc(productId, since).stream()
                .map(p -> new PricePoint(p.getOffer().getShop().getId(), p.getOffer().getShop().getName(), p.getPrice(),
                        p.getShippingCost(), p.isAvailable(), p.getSource().name(), p.getFetchedAt()))
                .toList();
    }

    private OffersResponse response(Product product, List<Offer> productOffers, List<String> refreshing) {
        Instant now = clock.instant();
        List<OfferView> views = new ArrayList<>();
        for (Offer offer : productOffers) {
            Optional<PriceRecord> latest = prices.findFirstByOffer_IdOrderByFetchedAtDesc(offer.getId());
            if (latest.isEmpty()) {
                continue;
            }
            PriceRecord price = latest.get();
            Optional<ProductMatch> match = matches.findFirstByOffer_IdAndProduct_Id(offer.getId(), product.getId());
            Long age = offer.getLastFetchedAt() == null ? null
                    : Duration.between(offer.getLastFetchedAt(), now).toSeconds();
            views.add(new OfferView(offer.getId(), offer.getShop().getId(), offer.getShop().getName(), offer.getName(),
                    price.getPrice(), price.getShippingCost(), price.isAvailable(), offer.getUrl(),
                    offer.getLastFetchedAt(), age,
                    match.map(m -> m.getMethod().name()).orElse(null),
                    match.map(ProductMatch::getConfidence).orElse(null),
                    match.map(m -> m.getMethod() != MatchMethod.IDENTIFIER && m.getMethod() != MatchMethod.MANUAL)
                            .orElse(false)));
        }
        views.sort(Comparator.comparing(OfferView::available).reversed()
                .thenComparing(OfferView::price, Comparator.nullsLast(Comparator.naturalOrder())));
        return new OffersResponse(product.getId(), product.getName(), popularity.isPopular(product.getId()),
                !refreshing.isEmpty(), refreshing, views, now);
    }

    private ProductSummary summary(Product product) {
        List<Offer> productOffers = offers.findByProduct_Id(product.getId());
        BigDecimal lowest = productOffers.stream()
                .map(o -> prices.findFirstByOffer_IdOrderByFetchedAtDesc(o.getId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(PriceRecord::isAvailable)
                .map(PriceRecord::getPrice)
                .min(BigDecimal::compareTo).orElse(null);
        return new ProductSummary(product.getId(), product.getName(), product.getBrand(), product.getModel(),
                product.getCategory(), product.getGtin(), product.getVariantAttributes(), lowest, productOffers.size());
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produkt " + id + " nie istnieje"));
    }
}
