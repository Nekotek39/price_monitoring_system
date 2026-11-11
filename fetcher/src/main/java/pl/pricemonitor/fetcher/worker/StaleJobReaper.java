package pl.pricemonitor.fetcher.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.pricemonitor.fetcher.FetcherProperties;
import pl.pricemonitor.fetcher.queue.FetchJobQueue;

/** Returns jobs left IN_PROGRESS by a crashed or restarted worker to the queue. */
@Component
public class StaleJobReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleJobReaper.class);

    private final FetchJobQueue queue;
    private final FetcherProperties properties;

    public StaleJobReaper(FetchJobQueue queue, FetcherProperties properties) {
        this.queue = queue;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void requeueStaleJobs() {
        int count = queue.requeueStale(properties.staleJobTimeout());
        if (count > 0) {
            log.warn("Returned {} interrupted jobs to the queue", count);
        }
    }
}
