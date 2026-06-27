package com.betx.application;

import java.time.Instant;

/** Metrics for one recommendation_id matching preview scope. */
public record DiagnosticsRecommendationIdMatchingScope(
    String scope,
    Instant cutoff,
    long paperTradesTotal,
    long paperTradesWithRecommendationId,
    long paperTradesEligible,
    long realBetsTotal,
    long realBetsWithRecommendationId,
    long realBetsEligible,
    long recommendationsWithBothPaperAndReal,
    long recommendationsWithPaperOnly,
    long recommendationsWithRealOnly,
    long recommendationsWithNeither,
    long recommendationIdPairs,
    long recommendationIdPaperOnly,
    long recommendationIdRealOnly,
    long recommendationIdAmbiguous,
    long ambiguousManyPaperToOneReal,
    long ambiguousOnePaperToManyReal,
    long ambiguousManyToMany,
    DiagnosticsRecommendationLegacyComparison legacyComparison
) {
    public DiagnosticsRecommendationIdMatchingScope {
        scope = scope == null || scope.isBlank() ? "unknown" : scope;
        legacyComparison = legacyComparison == null ? DiagnosticsRecommendationLegacyComparison.empty() : legacyComparison;
    }

    public static DiagnosticsRecommendationIdMatchingScope empty() {
        return new DiagnosticsRecommendationIdMatchingScope(
            "unavailable",
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            DiagnosticsRecommendationLegacyComparison.empty()
        );
    }
}
