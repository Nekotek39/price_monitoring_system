package pl.pricemonitor.api.admin;

import java.time.Instant;

public record ShopView(Long id, String name, String type, String feedFormat, String feedUrl, String status,
                       Instant unavailableUntil) {
}
