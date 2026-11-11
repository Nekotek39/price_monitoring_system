package pl.pricemonitor.fetcher.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import pl.pricemonitor.core.feed.FeedFormat;
import pl.pricemonitor.core.feed.FeedOffer;
import pl.pricemonitor.core.feed.FeedParseException;
import pl.pricemonitor.core.feed.FeedParser;
import pl.pricemonitor.fetcher.FetcherProperties;
import pl.pricemonitor.fetcher.feed.FeedClient;
import pl.pricemonitor.fetcher.feed.ShopRateLimiter;
import pl.pricemonitor.fetcher.queue.ClaimedJob;
import pl.pricemonitor.fetcher.queue.FetchJobQueue;
import pl.pricemonitor.fetcher.update.OfferUpdater;
import pl.pricemonitor.fetcher.update.UpdateResult;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.repository.ShopRepository;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Takes jobs from the queue, downloads the shop price file, parses it and stores the result.
 * Network errors are retried with exponential backoff; after the last attempt the job fails and the shop
 * is skipped for a while. A malformed file fails the job immediately (retrying would not help).
 */
@Component
public class FetchWorker {

    private static final Logger log = LoggerFactory.getLogger(FetchWorker.class);

    private final FetchJobQueue queue;
    private final ShopRepository shops;
    private final FeedClient client;
    private final ShopRateLimiter rateLimiter;
    private final OfferUpdater updater;
    private final ShopAvailability availability;
    private final FetcherProperties properties;
    private final Clock clock;
    private final Map<FeedFormat, FeedParser> parsers = new EnumMap<>(FeedFormat.class);

    public FetchWorker(FetchJobQueue queue, ShopRepository shops, FeedClient client, ShopRateLimiter rateLimiter,
                       OfferUpdater updater, ShopAvailability availability, List<FeedParser> parsers,
                       FetcherProperties properties, Clock clock) {
        this.queue = queue;
        this.shops = shops;
        this.client = client;
        this.rateLimiter = rateLimiter;
        this.updater = updater;
        this.availability = availability;
        this.properties = properties;
        this.clock = clock;
        parsers.forEach(p -> this.parsers.put(p.format(), p));
    }

    @Scheduled(fixedDelayString = "${pricemonitor.fetcher.poll-interval-ms}")
    public void poll() {
        for (int i = 0; i < properties.maxJobsPerPoll(); i++) {
            Optional<ClaimedJob> job = queue.claimNext();
            if (job.isEmpty()) {
                return;
            }
            process(job.get());
        }
    }

    void process(ClaimedJob job) {
        long started = System.nanoTime();
        Shop shop = shops.findById(job.shopId()).orElse(null);
        if (shop == null || shop.getFeedUrl() == null || shop.getFeedFormat() == null) {
            queue.fail(job.id(), "Sklep nie ma skonfigurowanego pliku cenowego");
            return;
        }
        try {
            rateLimiter.awaitTurn(shop.getId(), shop.getMinRequestIntervalSeconds());
            byte[] body = client.download(shop.getFeedUrl());
            FeedParser parser = parsers.get(shop.getFeedFormat());
            if (parser == null) {
                throw new FeedParseException("No parser for format " + shop.getFeedFormat());
            }
            List<FeedOffer> feed = parser.parse(new ByteArrayInputStream(body));
            UpdateResult result = updater.apply(job, feed, clock.instant());
            queue.complete(job.id());
            availability.markAvailable(shop.getId());
            log.info("Job {} ({}, shop '{}') done in {} ms: {} offers, {} new, {} price changes", job.id(), job.type(),
                    shop.getName(), Duration.ofNanos(System.nanoTime() - started).toMillis(), result.updated(),
                    result.created(), result.priceChanges());
        } catch (FeedParseException e) {
            log.warn("Job {}: invalid price file of shop '{}': {}", job.id(), shop.getName(), e.getMessage());
            queue.fail(job.id(), "Niepoprawny plik cenowy: " + e.getMessage());
        } catch (RestClientException e) {
            handleNetworkError(job, shop, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            queue.retryLater(job.id(), clock.instant(), "Przerwano");
        } catch (RuntimeException e) {
            log.error("Job {} failed unexpectedly", job.id(), e);
            queue.fail(job.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handleNetworkError(ClaimedJob job, Shop shop, RestClientException e) {
        String message = "Błąd pobierania (" + shop.getName() + "): " + e.getMessage();
        if (job.attempts() < properties.maxAttempts()) {
            Duration delay = properties.retryBaseDelay().multipliedBy(1L << Math.min(job.attempts() - 1, 10));
            Instant retryAt = clock.instant().plus(delay);
            log.warn("Job {} attempt {} failed, retry in {} s: {}", job.id(), job.attempts(), delay.toSeconds(),
                    e.getMessage());
            queue.retryLater(job.id(), retryAt, message);
        } else {
            log.error("Job {} failed after {} attempts, shop '{}' marked unavailable", job.id(), job.attempts(),
                    shop.getName());
            queue.fail(job.id(), message);
            availability.markUnavailable(shop.getId(), clock.instant().plus(properties.shopUnavailableFor()));
        }
    }
}
