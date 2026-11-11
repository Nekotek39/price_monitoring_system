package pl.pricemonitor.persistence.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.persistence.entity.Setting;
import pl.pricemonitor.persistence.repository.SettingRepository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Settings changed by the administrator at runtime, stored in table {@code ustawienie}
 * so that both the main application and the fetching process see the same values.
 */
@Service
public class SystemSettings {

    public static final String POPULARITY_THRESHOLD = "popularity.threshold";
    public static final String POPULARITY_WINDOW_DAYS = "popularity.window-days";
    public static final String MAX_DATA_AGE_MINUTES = "data.max-age-minutes";
    public static final String INDEXING_INTERVAL_MINUTES = "indexing.interval-minutes";
    public static final String INDEXING_MODE = "indexing.mode";

    private static final Map<String, String> DEFAULTS = Map.of(
            POPULARITY_THRESHOLD, "15",
            POPULARITY_WINDOW_DAYS, "7",
            MAX_DATA_AGE_MINUTES, "30",
            INDEXING_INTERVAL_MINUTES, "10",
            INDEXING_MODE, IndexingMode.DUAL.name());

    private static final Map<String, Predicate<String>> VALIDATORS = Map.of(
            POPULARITY_THRESHOLD, SystemSettings::isPositiveInt,
            POPULARITY_WINDOW_DAYS, SystemSettings::isPositiveInt,
            MAX_DATA_AGE_MINUTES, SystemSettings::isPositiveInt,
            INDEXING_INTERVAL_MINUTES, SystemSettings::isPositiveInt,
            INDEXING_MODE, v -> v.equals(IndexingMode.DUAL.name()) || v.equals(IndexingMode.CYCLIC_ONLY.name()));

    private final SettingRepository repository;

    public SystemSettings(SettingRepository repository) {
        this.repository = repository;
    }

    public int popularityThreshold() {
        return Integer.parseInt(get(POPULARITY_THRESHOLD));
    }

    public Duration popularityWindow() {
        return Duration.ofDays(Integer.parseInt(get(POPULARITY_WINDOW_DAYS)));
    }

    public Duration maxDataAge() {
        return Duration.ofMinutes(Integer.parseInt(get(MAX_DATA_AGE_MINUTES)));
    }

    public Duration indexingInterval() {
        return Duration.ofMinutes(Integer.parseInt(get(INDEXING_INTERVAL_MINUTES)));
    }

    public IndexingMode indexingMode() {
        return IndexingMode.valueOf(get(INDEXING_MODE));
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULTS.keySet().stream().sorted().forEach(k -> result.put(k, get(k)));
        return result;
    }

    /** Validates and stores the given settings; unknown keys or invalid values are rejected. */
    @Transactional
    public Map<String, String> update(Map<String, String> changes) {
        changes.forEach((key, value) -> {
            Predicate<String> validator = VALIDATORS.get(key);
            if (validator == null) {
                throw new IllegalArgumentException("Unknown setting: " + key);
            }
            if (value == null || !validator.test(value.trim())) {
                throw new IllegalArgumentException("Invalid value of " + key + ": " + value);
            }
        });
        changes.forEach((key, value) -> repository.findById(key).ifPresentOrElse(
                s -> s.setValue(value.trim()),
                () -> repository.save(new Setting(key, value.trim()))));
        return all();
    }

    private String get(String key) {
        return repository.findById(key).map(Setting::getValue).orElse(DEFAULTS.get(key));
    }

    private static boolean isPositiveInt(String v) {
        try {
            return Integer.parseInt(v) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
