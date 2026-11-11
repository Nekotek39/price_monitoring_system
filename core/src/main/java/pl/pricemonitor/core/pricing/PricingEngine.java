package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based repricing: trigger (competitor price change) -> strategy -> constraints.
 * Pure Java, no I/O, so it can be tested without a database or network.
 */
public class PricingEngine {

    private final Map<StrategyType, PricingStrategy> strategies = new EnumMap<>(StrategyType.class);
    private final List<PriceConstraint> constraints;

    /** Engine with all built-in strategies and the default constraint chain. */
    public PricingEngine() {
        this(List.of(new MatchLowestStrategy(), new UndercutByAmountStrategy(), new UndercutByPercentStrategy(),
                        new AverageBandStrategy()),
                List.of(new MinimumPriceConstraint(), new MaximumPriceConstraint(), new MaxChangeConstraint(),
                        new MinIntervalConstraint()));
    }

    public PricingEngine(List<PricingStrategy> strategies, List<PriceConstraint> constraints) {
        strategies.forEach(s -> this.strategies.put(s.type(), s));
        this.constraints = List.copyOf(constraints);
    }

    public PricingDecision decide(PricingInput input) {
        BigDecimal current = Money.round(input.currentPrice());
        List<CompetitorPrice> usable = input.competitorPrices().stream()
                .filter(CompetitorPrice::reliableMatch)
                .filter(CompetitorPrice::available)
                .toList();
        if (usable.isEmpty()) {
            return new PricingDecision(DecisionOutcome.NO_DATA, current, null, current, null, List.of(),
                    "Brak wiarygodnych cen konkurencji.");
        }
        BigDecimal lowest = Money.round(PricingStrategy.lowest(usable));

        Instant oldestAllowed = input.now().minus(input.rule().maxDataAge());
        List<Long> stale = usable.stream().filter(p -> p.fetchedAt().isBefore(oldestAllowed))
                .map(CompetitorPrice::shopId).distinct().toList();
        if (!stale.isEmpty()) {
            return new PricingDecision(DecisionOutcome.STALE_DATA, current, null, current, lowest, stale,
                    "Dane części sklepów są starsze niż " + input.rule().maxDataAge().toMinutes()
                            + " min; potrzebne pobranie „na żądanie”.");
        }

        PricingStrategy strategy = strategies.get(input.rule().strategy());
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for " + input.rule().strategy());
        }
        BigDecimal proposed = Money.round(strategy.propose(current, usable, input.rule().parameters()));
        CompetitorPrice cheapest = usable.stream().filter(p -> Money.round(p.price()).compareTo(lowest) == 0)
                .findFirst().orElseThrow();
        List<String> notes = new ArrayList<>();
        notes.add("Najniższa cena konkurencji: " + Money.format(lowest) + " (" + cheapest.shopName() + ").");

        BigDecimal price = proposed;
        for (PriceConstraint constraint : constraints) {
            ConstraintResult result = constraint.apply(price, input);
            if (result.note() != null) {
                notes.add(result.note());
            }
            if (result.stopped()) {
                return new PricingDecision(result.stop(), current, proposed, current, lowest, List.of(),
                        String.join(" ", notes));
            }
            price = Money.round(result.price());
        }

        if (price.compareTo(current) == 0) {
            notes.add("Cena " + Money.format(current) + " jest zgodna ze strategią.");
            return new PricingDecision(DecisionOutcome.NO_CHANGE, current, proposed, current, lowest, List.of(),
                    String.join(" ", notes));
        }
        notes.add("Nowa cena: " + Money.format(price) + " (było " + Money.format(current) + ").");
        return new PricingDecision(DecisionOutcome.CHANGE, current, proposed, price, lowest, List.of(),
                String.join(" ", notes));
    }
}
