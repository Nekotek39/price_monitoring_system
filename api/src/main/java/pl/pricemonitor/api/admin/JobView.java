package pl.pricemonitor.api.admin;

import java.time.Instant;

public record JobView(Long id, Long shopId, Long productId, String type, int priority, String status, int attempts,
                      String error, Instant createdAt, Instant startedAt, Instant finishedAt) {
}
