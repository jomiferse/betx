package com.betx.application;

import java.util.Map;

public record DiagnosticsBetRecommendationsSummary(
    long totalRecommendations,
    long pre22ShadowRows,
    long post22CanonicalRows,
    long canonicalActiveRecommendations,
    long canonicalCoveredRecommendations,
    long canonicalExpiredRecommendations,
    long recommendationObservations,
    double averageObservedCount,
    double medianObservedCount,
    double p95ObservedCount,
    Map<String, Long> topByObservedCount,
    long duplicateCanonicalGroups,
    long withEvaluationId,
    long withLastEvaluationId,
    long withStrategyName,
    long withSelectionSide,
    Map<String, Long> byStrategy,
    Map<String, Long> bySelectionSide,
    Map<String, Long> byCompetition,
    long createdInPeriod,
    long orphanRecommendations
) {
    public DiagnosticsBetRecommendationsSummary {
        topByObservedCount = topByObservedCount == null ? Map.of() : Map.copyOf(topByObservedCount);
        byStrategy = byStrategy == null ? Map.of() : Map.copyOf(byStrategy);
        bySelectionSide = bySelectionSide == null ? Map.of() : Map.copyOf(bySelectionSide);
        byCompetition = byCompetition == null ? Map.of() : Map.copyOf(byCompetition);
    }

    public static DiagnosticsBetRecommendationsSummary empty() {
        return new DiagnosticsBetRecommendationsSummary(
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
            Map.of(),
            0,
            0,
            0,
            0,
            0,
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            0
        );
    }
}
