package pl.pricemonitor.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.pricemonitor.persistence.repository.OfferRepository;

/** On the first start (no offers in the database) schedules a full import of all monitored shops. */
@Component
@ConditionalOnProperty(prefix = "pricemonitor.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupImport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupImport.class);

    private final OfferRepository offers;
    private final FetchJobScheduler scheduler;

    public StartupImport(OfferRepository offers, FetchJobScheduler scheduler) {
        this.offers = offers;
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (offers.count() == 0) {
            log.info("No offers in the database - scheduled initial import of {} shops", scheduler.scheduleImport());
        }
    }
}
