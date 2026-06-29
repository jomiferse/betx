package com.betx.application;

import java.time.Instant;
import java.util.List;

/** One recommendation_id divergence example rendered in diagnostics output and JSON. */
public record DiagnosticsRecommendationDivergenceExample(
    String recommendationId,
    String canonicalKey,
    String eventName,
    String runnerName,
    String marketId,
    long selectionId,
    String selectionSide,
    String strategyName,
    Instant firstSeenAt,
    Instant lastSeenAt,
    long paperCount,
    long realCount,
    String classification,
    DiagnosticsRecommendationDivergenceReason reason,
    List<DiagnosticsRecommendationDivergenceEvidence> evidence
) {
    public DiagnosticsRecommendationDivergenceExample {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        reason = reason == null ? DiagnosticsRecommendationDivergenceReason.UNKNOWN : reason;
    }
}
