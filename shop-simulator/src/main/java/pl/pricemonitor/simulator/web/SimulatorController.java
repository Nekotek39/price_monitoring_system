package pl.pricemonitor.simulator.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pricemonitor.simulator.ShopRegistry;
import pl.pricemonitor.simulator.scenario.ChangeEvent;
import pl.pricemonitor.simulator.scenario.ScenarioService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Control of the simulation: scenario, failure injection and ground truth for the experiments. */
@RestController
@RequestMapping("/simulator")
public class SimulatorController {

    /** True state of one offer (for checking the share of prices consistent with the shop). */
    public record TruthEntry(String shop, String offerId, String product, BigDecimal price, boolean available) {
    }

    private final ShopRegistry registry;
    private final ScenarioService scenario;

    public SimulatorController(ShopRegistry registry, ScenarioService scenario) {
        this.registry = registry;
        this.scenario = scenario;
    }

    /** Example: POST /simulator/scenario/start?durationHours=24&fraction=0.2&seed=1&timeScale=60 */
    @PostMapping("/scenario/start")
    public ScenarioService.ScenarioStatus start(@RequestParam(defaultValue = "24") double durationHours,
                                                @RequestParam(defaultValue = "0.2") double fraction,
                                                @RequestParam(defaultValue = "1") long seed,
                                                @RequestParam(defaultValue = "1") double timeScale) {
        Duration duration = Duration.ofMillis((long) (durationHours * 3_600_000));
        return scenario.start(new ScenarioService.ScenarioRequest(duration, fraction, seed, timeScale));
    }

    @PostMapping("/scenario/stop")
    public ScenarioService.ScenarioStatus stop() {
        scenario.stop();
        return scenario.status();
    }

    @GetMapping("/scenario")
    public ScenarioService.ScenarioStatus status() {
        return scenario.status();
    }

    @GetMapping("/changes")
    public List<ChangeEvent> changes() {
        return scenario.events();
    }

    /** Restores initial prices and availability of all shops. */
    @PostMapping("/reset")
    public Map<String, String> reset() throws IOException {
        scenario.stop();
        registry.reset();
        return Map.of("status", "reset");
    }

    @GetMapping("/truth")
    public List<TruthEntry> truth() {
        List<TruthEntry> result = new ArrayList<>();
        registry.shops().forEach(s -> s.offers().values().forEach(o -> result.add(
                new TruthEntry(s.code(), o.data().id(), o.data().truth(), o.price(), o.available()))));
        return result;
    }

    /** Example: POST /simulator/shops/s2/failure?mode=DOWN or ?mode=SLOW&delayMs=6000 or ?mode=NONE */
    @PostMapping("/shops/{code}/failure")
    public Map<String, Object> failure(@PathVariable String code, @RequestParam ShopRegistry.FailureMode mode,
                                       @RequestParam(defaultValue = "0") long delayMs) {
        registry.shop(code).setFailure(mode, delayMs);
        return Map.of("shop", code, "mode", mode, "delayMs", delayMs);
    }
}
