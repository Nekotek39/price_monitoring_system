package pl.pricemonitor.core.matching;

/** How an offer was matched to a product. */
public enum MatchMethod {
    /** Equal GTIN (EAN) or manufacturer part number (MPN). */
    IDENTIFIER,
    /** Brand and model found in the offer name and variant attributes equal. */
    ATTRIBUTES,
    /** Trigram name similarity only. */
    NAME,
    /** Set by a person. */
    MANUAL
}
