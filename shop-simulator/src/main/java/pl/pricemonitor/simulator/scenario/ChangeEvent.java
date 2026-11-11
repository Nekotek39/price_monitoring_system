package pl.pricemonitor.simulator.scenario;

import java.math.BigDecimal;
import java.time.Instant;

/** Executed price change (ground truth for measuring data consistency). */
public record ChangeEvent(Instant at, String shopCode, String offerId, String truth, BigDecimal oldPrice,
                          BigDecimal newPrice) {
}
