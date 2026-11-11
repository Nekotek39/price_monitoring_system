package pl.pricemonitor.core.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Multi-step matching of a shop offer to a catalog product:
 * <ol>
 *     <li>identifiers: GTIN (EAN), then MPN,</li>
 *     <li>brand and model in the offer name with equal variant attributes,</li>
 *     <li>trigram name similarity.</li>
 * </ol>
 * Offers of used or refurbished items are never matched. Variant attributes (color, storage, size, region,
 * bundle) must not conflict; an offer missing an attribute of the product goes to manual review.
 */
public class OfferMatcher {

    /** Words that denote a different model line when present in the offer but not in the product. */
    private static final Set<String> MODEL_SUFFIXES = Set.of("pro", "max", "ultra", "plus", "mini", "lite", "slim",
            "digital", "fe", "se", "air", "edge", "xl");

    private static final BigDecimal GTIN_CONFIDENCE = new BigDecimal("1.000");
    private static final BigDecimal MPN_CONFIDENCE = new BigDecimal("0.970");
    private static final BigDecimal IDENTIFIER_CONFLICT_CONFIDENCE = new BigDecimal("0.800");

    private final MatcherSettings settings;

    public OfferMatcher(MatcherSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public OfferMatcher() {
        this(MatcherSettings.defaults());
    }

    public Optional<MatchResult> match(OfferCandidate offer, List<ProductCandidate> products) {
        ProductCondition fromName = ProductCondition.fromName(offer.name());
        ProductCondition condition = fromName != ProductCondition.UNKNOWN ? fromName : offer.condition();
        if (condition == ProductCondition.USED || condition == ProductCondition.REFURBISHED) {
            return Optional.empty();
        }
        Map<String, String> offerAttributes = VariantAttributes.combine(offer.attributes(), offer.name());

        Optional<MatchResult> byIdentifier = matchByIdentifier(offer, offerAttributes, products);
        if (byIdentifier.isPresent()) {
            return byIdentifier;
        }
        return matchByNameAndAttributes(offer, offerAttributes, products);
    }

    private Optional<MatchResult> matchByIdentifier(OfferCandidate offer, Map<String, String> offerAttributes,
                                                    List<ProductCandidate> products) {
        String gtin = TextNormalizer.normalizeGtin(offer.gtin());
        String mpn = TextNormalizer.normalizeMpn(offer.mpn());
        if (gtin != null) {
            for (ProductCandidate p : products) {
                if (gtin.equals(TextNormalizer.normalizeGtin(p.gtin()))) {
                    return Optional.of(identifierResult(p, offerAttributes, GTIN_CONFIDENCE, "GTIN"));
                }
            }
        }
        if (mpn != null) {
            for (ProductCandidate p : products) {
                if (mpn.equals(TextNormalizer.normalizeMpn(p.mpn()))) {
                    return Optional.of(identifierResult(p, offerAttributes, MPN_CONFIDENCE, "MPN"));
                }
            }
        }
        return Optional.empty();
    }

    private MatchResult identifierResult(ProductCandidate p, Map<String, String> offerAttributes,
                                         BigDecimal confidence, String identifier) {
        Map<String, String> productAttributes = VariantAttributes.combine(p.attributes(), p.name());
        // An identifier denotes one variant, so missing offer attributes are fine; a conflict is suspicious.
        if (VariantAttributes.compare(productAttributes, offerAttributes) == VariantAttributes.Comparison.CONFLICT) {
            return new MatchResult(p.id(), MatchMethod.IDENTIFIER, IDENTIFIER_CONFLICT_CONFIDENCE, MatchStatus.TO_REVIEW,
                    "Zgodny " + identifier + ", ale atrybuty wariantu się różnią");
        }
        return new MatchResult(p.id(), MatchMethod.IDENTIFIER, confidence, MatchStatus.AUTOMATIC, "Zgodny " + identifier);
    }

    private Optional<MatchResult> matchByNameAndAttributes(OfferCandidate offer, Map<String, String> offerAttributes,
                                                           List<ProductCandidate> products) {
        String offerName = TextNormalizer.normalize(offer.name());
        Set<String> offerTokens = new HashSet<>(Arrays.asList(offerName.split(" ")));
        String offerBrand = offer.brand() == null ? null : TextNormalizer.normalize(offer.brand());

        ProductCandidate best = null;
        boolean bestModelHit = false;
        double bestSimilarity = -1;
        VariantAttributes.Comparison bestComparison = null;

        for (ProductCandidate p : products) {
            String brand = TextNormalizer.normalize(p.brand());
            if (offerBrand != null && !offerBrand.isEmpty() && !offerBrand.equals(brand)) {
                continue;
            }
            Map<String, String> productAttributes = VariantAttributes.combine(p.attributes(), p.name());
            VariantAttributes.Comparison comparison = VariantAttributes.compare(productAttributes, offerAttributes);
            if (comparison == VariantAttributes.Comparison.CONFLICT) {
                continue;
            }
            Set<String> productTokens = new HashSet<>(Arrays.asList(
                    TextNormalizer.normalize(p.brand() + " " + p.name() + " " + p.model()).split(" ")));
            if (hasExtraModelSuffix(offerTokens, productTokens)) {
                continue;
            }
            boolean modelHit = offerTokens.containsAll(Arrays.asList(TextNormalizer.normalize(p.model()).split(" ")));
            double similarity = TrigramSimilarity.similarity(offerName, p.brand() + " " + p.name());
            boolean better = best == null
                    || (modelHit && !bestModelHit)
                    || (modelHit == bestModelHit && similarity > bestSimilarity);
            if (better) {
                best = p;
                bestModelHit = modelHit;
                bestSimilarity = similarity;
                bestComparison = comparison;
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        double reviewFrom = settings.nameAcceptThreshold() - settings.reviewMargin();
        BigDecimal confidence = BigDecimal.valueOf(bestSimilarity).setScale(3, RoundingMode.HALF_UP);
        if (bestModelHit && bestSimilarity >= reviewFrom) {
            if (bestComparison == VariantAttributes.Comparison.INCOMPLETE) {
                return Optional.of(new MatchResult(best.id(), MatchMethod.ATTRIBUTES, confidence, MatchStatus.TO_REVIEW,
                        "Zgodna marka i model, ale brak części atrybutów wariantu"));
            }
            BigDecimal attrConfidence = BigDecimal.valueOf(Math.min(0.95, 0.85 + bestSimilarity * 0.1))
                    .setScale(3, RoundingMode.HALF_UP);
            return Optional.of(new MatchResult(best.id(), MatchMethod.ATTRIBUTES, attrConfidence, MatchStatus.AUTOMATIC,
                    "Zgodna marka, model i atrybuty wariantu"));
        }
        if (bestSimilarity >= settings.nameAcceptThreshold() && bestComparison == VariantAttributes.Comparison.EQUAL) {
            return Optional.of(new MatchResult(best.id(), MatchMethod.NAME, confidence, MatchStatus.AUTOMATIC,
                    "Podobieństwo nazw " + confidence));
        }
        if (bestSimilarity >= reviewFrom) {
            return Optional.of(new MatchResult(best.id(), MatchMethod.NAME, confidence, MatchStatus.TO_REVIEW,
                    "Podobieństwo nazw blisko progu: " + confidence));
        }
        return Optional.empty();
    }

    private static boolean hasExtraModelSuffix(Set<String> offerTokens, Set<String> productTokens) {
        for (String suffix : MODEL_SUFFIXES) {
            if (offerTokens.contains(suffix) && !productTokens.contains(suffix)) {
                return true;
            }
        }
        return false;
    }
}
