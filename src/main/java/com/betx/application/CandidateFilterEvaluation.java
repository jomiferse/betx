package com.betx.application;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;

public record CandidateFilterEvaluation(
    String id,
    String recommendationId,
    String canonicalKey,
    CandidateFilterName filterName,
    CandidateFilterDecision decision,
    CandidateFilterDecisionReason reason,
    SelectionSide selectionSide,
    BigDecimal odds,
    String strategyName,
    CandidateFilterSource source,
    Instant evaluatedAt,
    Instant createdAt,
    Instant lastEvaluatedAt,
    long observedCount
) {
    public CandidateFilterEvaluation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required.");
        }
        if (recommendationId == null || recommendationId.isBlank()) {
            throw new IllegalArgumentException("recommendationId is required.");
        }
        if (filterName == null || decision == null || reason == null || source == null) {
            throw new IllegalArgumentException("filterName, decision, reason, and source are required.");
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt is required.");
        }
        id = id.strip();
        recommendationId = recommendationId.strip();
        canonicalKey = canonicalKey == null || canonicalKey.isBlank() ? null : canonicalKey.strip();
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        strategyName = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        createdAt = createdAt == null ? evaluatedAt : createdAt;
        lastEvaluatedAt = lastEvaluatedAt == null ? evaluatedAt : lastEvaluatedAt;
        observedCount = Math.max(1, observedCount);
    }

    public CandidateFilterEvaluation withLatest(
        CandidateFilterDecision decision,
        CandidateFilterDecisionReason reason,
        BigDecimal odds,
        Instant evaluatedAt
    ) {
        return new CandidateFilterEvaluation(
            id,
            recommendationId,
            canonicalKey,
            filterName,
            decision,
            reason,
            selectionSide,
            odds,
            strategyName,
            source,
            this.evaluatedAt,
            createdAt,
            evaluatedAt,
            observedCount + 1
        );
    }
}
