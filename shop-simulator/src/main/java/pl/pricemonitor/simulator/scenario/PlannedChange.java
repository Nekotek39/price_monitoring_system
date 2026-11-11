package pl.pricemonitor.simulator.scenario;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Price change planned by the scenario.
 *
 * @param offset   time from the scenario start (already scaled)
 * @param shopCode shop code
 * @param offerId  offer id in the shop
 * @param percent  price change in percent (negative = price cut)
 */
public record PlannedChange(Duration offset, String shopCode, String offerId, BigDecimal percent) {
}
