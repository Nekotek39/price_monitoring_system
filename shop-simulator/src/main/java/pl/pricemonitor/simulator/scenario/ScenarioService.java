package pl.pricemonitor.simulator.scenario;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.pricemonitor.simulator.ShopRegistry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Runs the price change scenario and records every change. */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    /** Scenario parameters; {@code timeScale} 60 runs a 24 h scenario in 24 minutes. */
    public record ScenarioRequest(Duration duration, double fraction, long seed, double timeScale) {
    }

    public record ScenarioStatus(boolean running, Instant startedAt, int planned, int executed) {
    }

    private final ShopRegistry registry;
    private final Clock clock = Clock.systemUTC();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<ChangeEvent> events = new CopyOnWriteArrayList<>();
    private final List<ScheduledFuture<?>> pending = new ArrayList<>();
    private volatile Instant startedAt;
    private volatile int planned;

    public ScenarioService(ShopRegistry registry) {
        this.registry = registry;
    }

    public synchronized ScenarioStatus start(ScenarioRequest request) {
        stop();
        events.clear();
        Map<String, List<String>> offers = new LinkedHashMap<>();
        registry.shops().forEach(s -> offers.put(s.code(), new ArrayList<>(s.offers().keySet())));
        Duration scaled = Duration.ofMillis((long) (request.duration().toMillis() / Math.max(request.timeScale(), 1e-9)));
        List<PlannedChange> plan = ScenarioPlanner.plan(offers, request.fraction(), scaled, request.seed());
        startedAt = clock.instant();
        planned = plan.size();
        for (PlannedChange change : plan) {
            pending.add(executor.schedule(() -> execute(change), change.offset().toMillis(), TimeUnit.MILLISECONDS));
        }
        log.info("Scenario started: {} price changes within {} (time scale {})", plan.size(), scaled, request.timeScale());
        return status();
    }

    public synchronized void stop() {
        pending.forEach(f -> f.cancel(false));
        pending.clear();
    }

    public ScenarioStatus status() {
        boolean running = pending.stream().anyMatch(f -> !f.isDone());
        return new ScenarioStatus(running, startedAt, planned, events.size());
    }

    public List<ChangeEvent> events() {
        return Collections.unmodifiableList(events);
    }

    private void execute(PlannedChange change) {
        ShopRegistry.OfferState offer = registry.shop(change.shopCode()).offers().get(change.offerId());
        if (offer == null) {
            return;
        }
        BigDecimal old = offer.price();
        BigDecimal updated = ScenarioPlanner.apply(old, change.percent());
        offer.setPrice(updated);
        events.add(new ChangeEvent(clock.instant(), change.shopCode(), change.offerId(), offer.data().truth(), old,
                updated));
        log.info("Price change {}/{}: {} -> {}", change.shopCode(), change.offerId(), old, updated);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
