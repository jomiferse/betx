package com.betx.application;

import java.util.List;
import java.util.Map;

/** Diagnostics-only explanation of paper/real recommendation_id preview divergence. */
public record DiagnosticsRecommendationDivergenceAnalysis(
    long paperOnlyRecommendations,
    long realOnlyRecommendations,
    long ambiguousRecommendations,
    Map<DiagnosticsRecommendationDivergenceReason, Long> paperOnlyReasonBreakdown,
    Map<DiagnosticsRecommendationDivergenceReason, Long> realOnlyReasonBreakdown,
    long unknownPaperOnly,
    long unknownRealOnly,
    List<DiagnosticsRecommendationDivergenceExample> topPaperOnlyExamples,
    List<DiagnosticsRecommendationDivergenceExample> topRealOnlyExamples
) {
    public DiagnosticsRecommendationDivergenceAnalysis {
        paperOnlyReasonBreakdown = paperOnlyReasonBreakdown == null ? Map.of() : Map.copyOf(paperOnlyReasonBreakdown);
        realOnlyReasonBreakdown = realOnlyReasonBreakdown == null ? Map.of() : Map.copyOf(realOnlyReasonBreakdown);
        topPaperOnlyExamples = topPaperOnlyExamples == null ? List.of() : List.copyOf(topPaperOnlyExamples);
        topRealOnlyExamples = topRealOnlyExamples == null ? List.of() : List.copyOf(topRealOnlyExamples);
    }

    public static DiagnosticsRecommendationDivergenceAnalysis empty() {
        return new DiagnosticsRecommendationDivergenceAnalysis(0, 0, 0, Map.of(), Map.of(), 0, 0, List.of(), List.of());
    }
}
