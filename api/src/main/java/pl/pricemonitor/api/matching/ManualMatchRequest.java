package pl.pricemonitor.api.matching;

import jakarta.validation.constraints.NotNull;

public record ManualMatchRequest(@NotNull Long offerId, @NotNull Long productId, Long reviewerId) {
}
