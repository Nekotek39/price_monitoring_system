package pl.pricemonitor.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.pricemonitor.api.matching.MatchingService;
import pl.pricemonitor.api.notification.NotificationService;
import pl.pricemonitor.api.pricing.PricingService;
import pl.pricemonitor.api.product.OfferStreamService;
import pl.pricemonitor.api.product.ProductService;
import pl.pricemonitor.persistence.entity.FetchJob;
import pl.pricemonitor.persistence.entity.JobStatus;
import pl.pricemonitor.persistence.entity.JobType;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.repository.FetchJobRepository;
import pl.pricemonitor.persistence.repository.PriceRecordRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reacts to jobs finished by the fetching process: runs offer matching after an import, triggers the
 * dynamic pricing module for products whose prices changed and pushes fresh offers to SSE subscribers.
 */
@Component
public class JobCompletionWatcher {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionWatcher.class);

    private final FetchJobRepository jobs;
    private final ShopRepository shops;
    private final PriceRecordRepository prices;
    private final MatchingService matching;
    private final PricingService pricing;
    private final ProductService products;
    private final OfferStreamService stream;
    private final NotificationService notifications;
    private Instant lastSeen;

    public JobCompletionWatcher(FetchJobRepository jobs, ShopRepository shops, PriceRecordRepository prices,
                                MatchingService matching, PricingService pricing, ProductService products,
                                OfferStreamService stream, NotificationService notifications) {
        this.jobs = jobs;
        this.shops = shops;
        this.prices = prices;
        this.matching = matching;
        this.pricing = pricing;
        this.products = products;
        this.stream = stream;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 2_000)
    public void poll() {
        if (lastSeen == null) {
            Instant last = jobs.findLastFinishedAt();
            lastSeen = last == null ? Instant.EPOCH : last;
        }
        List<FetchJob> finished = jobs.findByStatusInAndFinishedAtAfterOrderByFinishedAtAsc(
                EnumSet.of(JobStatus.DONE, JobStatus.FAILED), lastSeen);
        for (FetchJob job : finished) {
            lastSeen = job.getFinishedAt();
            try {
                handle(job);
            } catch (RuntimeException e) {
                log.error("Handling of finished job {} failed", job.getId(), e);
            }
        }
    }

    void handle(FetchJob job) {
        Long shopId = job.getShop().getId();
        Long productId = job.getProduct() == null ? null : job.getProduct().getId();
        if (job.getStatus() == JobStatus.FAILED) {
            String shopName = shops.findById(shopId).map(Shop::getName).orElse("#" + shopId);
            notifications.adminAlert("Błąd pobierania danych: " + shopName,
                    "Zadanie " + job.getId() + " (" + job.getType() + ") zakończyło się błędem: " + job.getError());
            if (productId != null) {
                stream.publish(productId, "error", Map.of("shop", shopName,
                        "message", "Nie udało się odświeżyć danych sklepu " + shopName
                                + " - pokazujemy ostatnią znaną cenę."));
            }
            return;
        }
        if (job.getType() == JobType.IMPORT) {
            matching.matchNewOffers();
        }
        Instant since = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        List<Long> changed = prices.findProductIdsWithPriceChanges(shopId, since);
        if (!changed.isEmpty()) {
            pricing.onPricesChanged(changed);
        }
        Set<Long> toNotify = new HashSet<>(stream.subscribedProducts());
        if (job.getType() == JobType.ON_DEMAND) {
            toNotify.retainAll(productId == null ? Set.of() : Set.of(productId));
        }
        for (Long id : toNotify) {
            stream.publish(id, "offers", products.snapshot(id));
        }
    }
}
