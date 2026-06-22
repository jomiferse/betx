package com.betx.application;

import java.util.Map;

public record DiagnosticsBetRecommendationsSummary(
    long totalRecommendations,
    long withEvaluationId,
    long withStrategyName,
    long withSelectionSide,
    Map<String, Long> byStrategy,
    Map<String, Long> bySelectionSide,
    Map<String, Long> byCompetition,
    long createdInPeriod,
    long orphanRecommendations
) {
    public DiagnosticsBetRecommendationsSummary {
        byStrategy = byStrategy == null ? Map.of() : Map.copyOf(byStrategy);
        bySelectionSide = bySelectionSide == null ? Map.of() : Map.copyOf(bySelectionSide);
        byCompetition = byCompetition == null ? Map.of() : Map.copyOf(byCompetition);
    }

    public static DiagnosticsBetRecommendationsSummary empty() {
        return new DiagnosticsBetRecommendationsSummary(0, 0, 0, 0, Map.of(), Map.of(), Map.of(), 0, 0);
    }
}
