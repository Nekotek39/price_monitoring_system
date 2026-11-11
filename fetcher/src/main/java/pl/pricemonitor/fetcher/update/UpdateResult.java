package pl.pricemonitor.fetcher.update;

/**
 * @param updated      offers confirmed by this job
 * @param created      new offers (import only)
 * @param priceChanges new price history rows (price, shipping or availability changed)
 */
public record UpdateResult(int updated, int created, int priceChanges) {
}
