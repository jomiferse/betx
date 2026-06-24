package com.betx.application;

public record DiagnosticsPaperRecommendationCoverage(
    long paperTradesTotal,
    long paperTradesWithRecommendationId,
    long paperTradesWithoutRecommendationId,
    long post23PaperTrades,
    long post23PaperTradesWithRecommendationId,
    long paperTradesWithRecommendationIdButMissingBetRecommendation,
    long paperTradesLinkedToCanonicalRecommendation,
    long paperTradesLinkedToActiveRecommendations,
    long paperTradesLinkedToCoveredRecommendations
) {
    public static DiagnosticsPaperRecommendationCoverage empty() {
        return new DiagnosticsPaperRecommendationCoverage(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
