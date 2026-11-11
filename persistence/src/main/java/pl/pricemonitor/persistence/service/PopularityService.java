package pl.pricemonitor.persistence.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.persistence.repository.SearchLogRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Popularity = number of searches (product views) in a time window. Popular products are refreshed
 * cyclically; the rest is fetched on demand (long tail).
 */
@Service
public class PopularityService {

    private final SearchLogRepository searchLogs;
    private final SystemSettings settings;
    private final Clock clock;

    public PopularityService(SearchLogRepository searchLogs, SystemSettings settings, Clock clock) {
        this.searchLogs = searchLogs;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<Long> popularProductIds() {
        Instant since = clock.instant().minus(settings.popularityWindow());
        return new HashSet<>(searchLogs.findPopularProductIds(since, settings.popularityThreshold()));
    }

    @Transactional(readOnly = true)
    public boolean isPopular(Long productId) {
        Instant since = clock.instant().minus(settings.popularityWindow());
        return searchLogs.countByProduct_IdAndCreatedAtAfter(productId, since) >= settings.popularityThreshold();
    }
}
