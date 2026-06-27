package com.betx.application;

/** Comparison between official legacy matching and preview recommendation_id matching. */
public record DiagnosticsRecommendationLegacyComparison(
    long legacyMatchedPairs,
    long recommendationIdMatchedPairs,
    long matchedByBoth,
    long legacyOnlyMatches,
    long recommendationOnlyMatches,
    long conflictingMatches,
    long legacyRealOnlyButRecommendationMatched,
    long legacyPaperOnlyButRecommendationMatched,
    long legacyAmbiguousResolvedByRecommendationId,
    long recommendationAmbiguousButLegacyMatched
) {
    public static DiagnosticsRecommendationLegacyComparison empty() {
        return new DiagnosticsRecommendationLegacyComparison(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
