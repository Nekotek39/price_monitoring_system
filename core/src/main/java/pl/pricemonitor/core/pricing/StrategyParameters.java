package pl.pricemonitor.core.pricing;

import java.math.BigDecimal;
import java.util.Map;

/** Reads numeric strategy parameters stored as strings. */
final class StrategyParameters {

    private StrategyParameters() {
    }

    static BigDecimal decimal(Map<String, String> parameters, String key, BigDecimal defaultValue) {
        String raw = parameters.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + key + "' is not a number: " + raw, e);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Parameter '" + key + "' must not be negative: " + raw);
        }
        return value;
    }
}
