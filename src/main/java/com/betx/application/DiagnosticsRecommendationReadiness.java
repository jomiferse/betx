package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.util.List;

/** Read-only readiness view for recommendation-based paper/real traceability. */
public record DiagnosticsRecommendationReadiness(
    long totalCanonicalRecommendations,
    long activeRecommendations,
    long coveredRecommendations,
    long expiredRecommendations,
    long recommendationsWithPaperTrades,
    long recommendationsWithoutPaperTrades,
    long recommendationsWithRealEquivalentBet,
    long recommendationsWithoutRealEquivalentBet,
    long recommendationsWithBothPaperAndRealEquivalent,
    long recommendationsWithPaperOnly,
    long recommendationsWithRealOnly,
    long recommendationsWithNeitherPaperNorReal,
    long paperTradesWithRecommendationId,
    long paperTradesMissingRecommendationIdPost23,
    long brokenPaperRecommendationJoins,
    long realBetsWithRecommendationId,
    long realBetsMissingRecommendationId,
    DiagnosticsDataProvenance realEquivalentCoverageSource,
    String readyForRealConsumption,
    String readyForRecommendationIdMatching,
    String readinessStatus,
    List<String> readinessReasons
) {
    public DiagnosticsRecommendationReadiness {
        realEquivalentCoverageSource = realEquivalentCoverageSource == null
            ? DiagnosticsDataProvenance.UNAVAILABLE
            : realEquivalentCoverageSource;
        readyForRealConsumption = normalizeStatus(readyForRealConsumption, "PARTIAL");
        readyForRecommendationIdMatching = normalizeStatus(readyForRecommendationIdMatching, "NO");
        readinessStatus = normalizeStatus(readinessStatus, readyForRealConsumption);
        readinessReasons = readinessReasons == null ? List.of() : List.copyOf(readinessReasons);
    }

    public static DiagnosticsRecommendationReadiness empty() {
        return new DiagnosticsRecommendationReadiness(
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
            DiagnosticsDataProvenance.UNAVAILABLE,
            "PARTIAL",
            "NO",
            "INSUFFICIENT_SAMPLE",
            List.of("No recommendation readiness data is available.")
        );
    }

    public DiagnosticsRecommendationReadiness withReadiness(
        String readyForRealConsumption,
        String readyForRecommendationIdMatching,
        String readinessStatus,
        List<String> readinessReasons
    ) {
        return new DiagnosticsRecommendationReadiness(
            totalCanonicalRecommendations,
            activeRecommendations,
            coveredRecommendations,
            expiredRecommendations,
            recommendationsWithPaperTrades,
            recommendationsWithoutPaperTrades,
            recommendationsWithRealEquivalentBet,
            recommendationsWithoutRealEquivalentBet,
            recommendationsWithBothPaperAndRealEquivalent,
            recommendationsWithPaperOnly,
            recommendationsWithRealOnly,
            recommendationsWithNeitherPaperNorReal,
            paperTradesWithRecommendationId,
            paperTradesMissingRecommendationIdPost23,
            brokenPaperRecommendationJoins,
            realBetsWithRecommendationId,
            realBetsMissingRecommendationId,
            realEquivalentCoverageSource,
            readyForRealConsumption,
            readyForRecommendationIdMatching,
            readinessStatus,
            readinessReasons
        );
    }

    private static String normalizeStatus(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
