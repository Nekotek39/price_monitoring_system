package pl.pricemonitor.fetcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled workers run unless {@code pricemonitor.scheduling.enabled=false} (used in integration tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pricemonitor.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
