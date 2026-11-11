package pl.pricemonitor.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.pricemonitor.api.config.ApiProperties;
import pl.pricemonitor.persistence.service.IndexingMode;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.time.Clock;
import java.time.Instant;

/**
 * Cyclic path of the dual model. The interval and the mode are read from the settings table at every tick,
 * so the administrator can change them without a restart.
 * <ul>
 *     <li>DUAL: cyclic refresh of popular products every interval + a full import every {@code import-interval},</li>
 *     <li>CYCLIC_ONLY (baseline for the experiment): full refresh of all offers every interval.</li>
 * </ul>
 */
@Component
public class IndexingScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexingScheduler.class);

    private final FetchJobScheduler scheduler;
    private final SystemSettings settings;
    private final ApiProperties properties;
    private final Clock clock;
    private Instant lastCycle;
    private Instant lastImport;

    public IndexingScheduler(FetchJobScheduler scheduler, SystemSettings settings, ApiProperties properties,
                             Clock clock) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    public void tick() {
        Instant now = clock.instant();
        if (lastImport == null) {
            // the startup import is scheduled by StartupImport
            lastImport = now;
        }
        if (lastCycle != null && lastCycle.plus(settings.indexingInterval()).isAfter(now)) {
            return;
        }
        lastCycle = now;
        if (settings.indexingMode() == IndexingMode.CYCLIC_ONLY) {
            int n = scheduler.scheduleImport();
            log.info("Cyclic-only model: scheduled full refresh of {} shops", n);
            return;
        }
        int n = scheduler.scheduleCyclic();
        log.info("Dual model: scheduled cyclic refresh of popular products in {} shops", n);
        if (lastImport.plus(properties.importInterval()).isBefore(now)) {
            lastImport = now;
            log.info("Scheduled periodic import of {} shops", scheduler.scheduleImport());
        }
    }
}
