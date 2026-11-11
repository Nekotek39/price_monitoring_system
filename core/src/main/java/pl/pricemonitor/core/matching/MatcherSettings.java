package pl.pricemonitor.core.matching;

/**
 * Thresholds of name-based matching.
 *
 * @param nameAcceptThreshold trigram similarity from which a name-based match is accepted
 * @param reviewMargin        pairs within this distance below the threshold go to manual review
 */
public record MatcherSettings(double nameAcceptThreshold, double reviewMargin) {

    public static MatcherSettings defaults() {
        return new MatcherSettings(0.55, 0.10);
    }

    public MatcherSettings {
        if (nameAcceptThreshold <= 0 || nameAcceptThreshold > 1 || reviewMargin < 0 || reviewMargin >= nameAcceptThreshold) {
            throw new IllegalArgumentException("Invalid matcher thresholds");
        }
    }
}
