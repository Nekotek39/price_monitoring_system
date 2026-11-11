package pl.pricemonitor.core.matching;

import java.math.BigDecimal;

/**
 * Proposed match of an offer to a product.
 *
 * @param productId  matched product
 * @param method     how it was matched
 * @param confidence 0.000 - 1.000
 * @param status     AUTOMATIC or TO_REVIEW
 * @param note       short explanation for the reviewer
 */
public record MatchResult(long productId, MatchMethod method, BigDecimal confidence, MatchStatus status, String note) {
}
