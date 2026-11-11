package pl.pricemonitor.api.matching;

/**
 * @param processed offers checked
 * @param automatic matched automatically
 * @param toReview  waiting for manual verification
 * @param unmatched no candidate (or used / refurbished items)
 */
public record MatchingSummary(int processed, int automatic, int toReview, int unmatched) {
}
