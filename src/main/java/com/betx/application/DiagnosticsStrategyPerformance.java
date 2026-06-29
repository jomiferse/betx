package com.betx.application;

import java.util.List;
import java.util.Map;

/** Diagnostics-only strategy performance breakdowns. */
public record DiagnosticsStrategyPerformance(
    DiagnosticsStrategyPerformanceSegment allTime,
    List<DiagnosticsStrategyPerformanceSegment> scopes,
    Map<String, DiagnosticsStrategyPerformanceSegment> bySelectionSide,
    Map<String, DiagnosticsStrategyPerformanceSegment> byOddsRange,
    Map<String, DiagnosticsStrategyPerformanceSegment> byCompetition,
    Map<String, DiagnosticsStrategyPerformanceSegment> byStrategy,
    Map<String, DiagnosticsStrategyPerformanceSegment> byDay,
    Map<String, DiagnosticsStrategyPerformanceSegment> byMonth,
    Map<String, DiagnosticsStrategyPerformanceSegment> byHourBucket,
    Map<String, DiagnosticsStrategyPerformanceSegment> byTimeToEventBucket,
    Map<String, DiagnosticsStrategyPerformanceSegment> byRecommendationStatus,
    Map<String, DiagnosticsStrategyPerformanceSegment> byRecommendationMatchingClassification,
    List<String> unavailableMetrics
) {
    public DiagnosticsStrategyPerformance {
        allTime = allTime == null ? DiagnosticsStrategyPerformanceSegment.empty("all-time") : allTime;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        bySelectionSide = bySelectionSide == null ? Map.of() : Map.copyOf(bySelectionSide);
        byOddsRange = byOddsRange == null ? Map.of() : Map.copyOf(byOddsRange);
        byCompetition = byCompetition == null ? Map.of() : Map.copyOf(byCompetition);
        byStrategy = byStrategy == null ? Map.of() : Map.copyOf(byStrategy);
        byDay = byDay == null ? Map.of() : Map.copyOf(byDay);
        byMonth = byMonth == null ? Map.of() : Map.copyOf(byMonth);
        byHourBucket = byHourBucket == null ? Map.of() : Map.copyOf(byHourBucket);
        byTimeToEventBucket = byTimeToEventBucket == null ? Map.of() : Map.copyOf(byTimeToEventBucket);
        byRecommendationStatus = byRecommendationStatus == null ? Map.of() : Map.copyOf(byRecommendationStatus);
        byRecommendationMatchingClassification = byRecommendationMatchingClassification == null
            ? Map.of()
            : Map.copyOf(byRecommendationMatchingClassification);
        unavailableMetrics = unavailableMetrics == null ? List.of() : List.copyOf(unavailableMetrics);
    }

    public static DiagnosticsStrategyPerformance empty() {
        return new DiagnosticsStrategyPerformance(
            DiagnosticsStrategyPerformanceSegment.empty("all-time"),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of()
        );
    }
}
