package pl.pricemonitor.core.matching;

import java.util.HashSet;
import java.util.Set;

/**
 * Trigram similarity computed like PostgreSQL pg_trgm: every word is padded with two spaces in front and one
 * at the end, split into three-character fragments, and the similarity is |A ∩ B| / |A ∪ B|.
 */
public final class TrigramSimilarity {

    private TrigramSimilarity() {
    }

    public static double similarity(String a, String b) {
        Set<String> ta = trigrams(TextNormalizer.normalize(a));
        Set<String> tb = trigrams(TextNormalizer.normalize(b));
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> common = new HashSet<>(ta);
        common.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) common.size() / union.size();
    }

    static Set<String> trigrams(String normalized) {
        Set<String> result = new HashSet<>();
        for (String word : normalized.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = "  " + word + " ";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                result.add(padded.substring(i, i + 3));
            }
        }
        return result;
    }
}
