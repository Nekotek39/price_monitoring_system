package pl.pricemonitor.fetcher.feed;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the minimum interval between requests to one shop (polite crawling, avoids being blocked). */
@Component
public class ShopRateLimiter {

    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    private final Map<Long, Instant> lastRequest = new ConcurrentHashMap<>();
    private final Clock clock;

    public ShopRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void awaitTurn(long shopId, int minIntervalSeconds) throws InterruptedException {
        Instant last = lastRequest.get(shopId);
        if (last != null) {
            Duration wait = Duration.between(clock.instant(), last.plusSeconds(minIntervalSeconds));
            if (!wait.isNegative() && !wait.isZero()) {
                Thread.sleep(Math.min(wait.toMillis(), MAX_WAIT.toMillis()));
            }
        }
        lastRequest.put(shopId, clock.instant());
    }
}
