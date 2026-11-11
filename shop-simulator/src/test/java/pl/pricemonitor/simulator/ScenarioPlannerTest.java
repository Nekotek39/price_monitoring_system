package pl.pricemonitor.simulator;

import org.junit.jupiter.api.Test;
import pl.pricemonitor.simulator.scenario.PlannedChange;
import pl.pricemonitor.simulator.scenario.ScenarioPlanner;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioPlannerTest {

    private static Map<String, List<String>> offers() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            a.add("A-" + i);
            b.add("B-" + i);
        }
        return Map.of("s1", a, "s2", b);
    }

    @Test
    void planIsDeterministicAndRespectsFractionAndDuration() {
        Duration day = Duration.ofHours(24);
        List<PlannedChange> first = ScenarioPlanner.plan(offers(), 0.2, day, 42);
        List<PlannedChange> second = ScenarioPlanner.plan(offers(), 0.2, day, 42);

        assertEquals(first, second);
        assertTrue(first.size() > 150 && first.size() < 250, "about 20% of 1000 offers, was " + first.size());
        for (int i = 1; i < first.size(); i++) {
            assertTrue(first.get(i - 1).offset().compareTo(first.get(i).offset()) <= 0);
        }
        assertTrue(first.stream().allMatch(c -> c.offset().compareTo(day) < 0));
        assertTrue(first.stream().allMatch(c -> c.percent().abs().compareTo(new BigDecimal("15")) <= 0));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioPlanner.plan(offers(), 1.5, Duration.ofHours(1), 1));
        assertThrows(IllegalArgumentException.class, () -> ScenarioPlanner.plan(offers(), 0.2, Duration.ZERO, 1));
    }

    @Test
    void appliesChangeWithShopStyleRounding() {
        assertEquals(new BigDecimal("1299.99"), ScenarioPlanner.apply(new BigDecimal("1529.00"), new BigDecimal("-15")));
        assertEquals(new BigDecimal("0.99"), ScenarioPlanner.apply(new BigDecimal("0.50"), new BigDecimal("-15")));
    }
}
