package pl.pricemonitor.simulator.scenario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plans the test scenario from section B1: during the scenario the prices of a given share of offers (default
 * 20%) change at random moments. The plan is deterministic for a given seed, so every repetition and both
 * compared models get exactly the same changes.
 */
public final class ScenarioPlanner {

    private ScenarioPlanner() {
    }

    /**
     * @param offersByShop offer ids per shop code
     * @param fraction     share of offers whose price changes (0..1)
     * @param duration     scenario length (e.g. 24 h, or 24 min when time is scaled)
     * @param seed         random seed
     */
    public static List<PlannedChange> plan(Map<String, List<String>> offersByShop, double fraction, Duration duration,
                                           long seed) {
        if (fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("fraction must be between 0 and 1");
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        Random random = new Random(seed);
        List<PlannedChange> plan = new ArrayList<>();
        offersByShop.keySet().stream().sorted().forEach(shop -> {
            for (String offerId : offersByShop.get(shop).stream().sorted().toList()) {
                if (random.nextDouble() >= fraction) {
                    continue;
                }
                long offsetMillis = (long) (random.nextDouble() * duration.toMillis());
                // Mostly price cuts (-15% .. -2%), sometimes increases (+2% .. +10%).
                double pct = random.nextDouble() < 0.7 ? -(2 + random.nextDouble() * 13) : 2 + random.nextDouble() * 8;
                plan.add(new PlannedChange(Duration.ofMillis(offsetMillis), shop, offerId,
                        BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP)));
            }
        });
        plan.sort(Comparator.comparing(PlannedChange::offset));
        return plan;
    }

    /** New price after the change, rounded to whole złoty minus 1 grosz (e.g. 1 299,99 -> typical shop price). */
    public static BigDecimal apply(BigDecimal price, BigDecimal percent) {
        BigDecimal raw = price.multiply(BigDecimal.ONE.add(percent.movePointLeft(2)));
        BigDecimal rounded = raw.setScale(0, RoundingMode.HALF_UP).subtract(new BigDecimal("0.01"));
        return rounded.max(new BigDecimal("0.99")).setScale(2, RoundingMode.HALF_UP);
    }
}
