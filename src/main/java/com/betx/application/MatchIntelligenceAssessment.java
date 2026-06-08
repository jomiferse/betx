package com.betx.application;

import java.util.List;

public record MatchIntelligenceAssessment(
    String exchange,
    String marketId,
    long selectionId,
    MatchIntelligenceDecision decision,
    int confidence,
    String summary,
    List<String> reasons,
    List<String> risks,
    List<MatchIntelligenceSource> sources
) {
    public MatchIntelligenceAssessment {
        decision = decision == null ? MatchIntelligenceDecision.UNAVAILABLE : decision;
        summary = summary == null || summary.isBlank() ? "No external intelligence summary available." : summary;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        risks = risks == null ? List.of() : List.copyOf(risks);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static MatchIntelligenceAssessment unavailable(String exchange, String marketId, long selectionId, String reason) {
        return new MatchIntelligenceAssessment(
            exchange,
            marketId,
            selectionId,
            MatchIntelligenceDecision.UNAVAILABLE,
            0,
            reason,
            List.of(reason),
            List.of(),
            List.of()
        );
    }
}
