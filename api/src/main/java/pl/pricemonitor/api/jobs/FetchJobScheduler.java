package pl.pricemonitor.api.jobs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.persistence.entity.FetchJob;
import pl.pricemonitor.persistence.entity.JobStatus;
import pl.pricemonitor.persistence.entity.JobType;
import pl.pricemonitor.persistence.entity.Offer;
import pl.pricemonitor.persistence.entity.Product;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.entity.ShopType;
import pl.pricemonitor.persistence.repository.FetchJobRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Puts jobs into the queue table; avoids duplicates of jobs that are already waiting or running. */
@Service
public class FetchJobScheduler {

    private static final Set<JobStatus> ACTIVE = EnumSet.of(JobStatus.PENDING, JobStatus.IN_PROGRESS);

    private final FetchJobRepository jobs;
    private final ShopRepository shops;
    private final SystemSettings settings;
    private final Clock clock;

    public FetchJobScheduler(FetchJobRepository jobs, ShopRepository shops, SystemSettings settings, Clock clock) {
        this.jobs = jobs;
        this.shops = shops;
        this.settings = settings;
        this.clock = clock;
    }

    /** Full import of all monitored shops (also discovers new offers). Returns the number of new jobs. */
    @Transactional
    public int scheduleImport() {
        int count = 0;
        for (Shop shop : shops.findByType(ShopType.MONITORED)) {
            if (!jobs.existsByShop_IdAndTypeAndStatusIn(shop.getId(), JobType.IMPORT, ACTIVE)) {
                jobs.save(FetchJob.importJob(shop, clock.instant()));
                count++;
            }
        }
        return count;
    }

    /** One cycle of cyclic indexing (popular products) for all monitored shops. */
    @Transactional
    public int scheduleCyclic() {
        int count = 0;
        for (Shop shop : shops.findByType(ShopType.MONITORED)) {
            if (!jobs.existsByShop_IdAndTypeAndStatusIn(shop.getId(), JobType.CYCLIC, ACTIVE)) {
                jobs.save(FetchJob.cyclic(shop, clock.instant()));
                count++;
            }
        }
        return count;
    }

    /**
     * On-demand path: for every shop whose offer of the product is older than the allowed data age,
     * schedules a high-priority job. Returns names of shops being refreshed.
     */
    @Transactional
    public List<String> refreshIfStale(Product product, Collection<Offer> offers) {
        Instant now = clock.instant();
        Instant oldestAllowed = now.minus(settings.maxDataAge());
        Map<Long, Shop> staleShops = new LinkedHashMap<>();
        for (Offer offer : offers) {
            Shop shop = offer.getShop();
            boolean stale = offer.getLastFetchedAt() == null || offer.getLastFetchedAt().isBefore(oldestAllowed);
            if (stale && shop.isAvailableAt(now)) {
                staleShops.putIfAbsent(shop.getId(), shop);
            }
        }
        return requestOnDemand(product, staleShops.values());
    }

    /** Schedules on-demand jobs of the product for the given shops (skips shops with a job already queued). */
    @Transactional
    public List<String> requestOnDemand(Product product, Collection<Shop> targetShops) {
        List<String> names = new ArrayList<>();
        for (Shop shop : targetShops) {
            if (!jobs.existsByShop_IdAndProduct_IdAndStatusIn(shop.getId(), product.getId(), ACTIVE)) {
                jobs.save(FetchJob.onDemand(shop, product, clock.instant()));
            }
            names.add(shop.getName());
        }
        return names;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJobs(Long shopId, Long productId) {
        return jobs.existsByShop_IdAndProduct_IdAndStatusIn(shopId, productId, ACTIVE);
    }
}
