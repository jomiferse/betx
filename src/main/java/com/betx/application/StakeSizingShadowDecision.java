package com.betx.application;

import com.betx.domain.order.SelectionSide;
import com.betx.domain.staking.StakeSizingBlockReason;
import com.betx.domain.staking.StakeSizingDecisionReason;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import java.math.BigDecimal;
import java.time.Instant;

/** Durable shadow-only stake sizing decision, safe for analytics persistence. */
public record StakeSizingShadowDecision(
    String id,
    String recommendationId,
    String canonicalKey,
    StakeSizingMode policyName,
    StakeSizingRiskProfile riskProfile,
    StakeSizingSource source,
    SelectionSide selectionSide,
    BigDecimal odds,
    String strategyName,
    BigDecimal baseStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    BigDecimal bankroll,
    BigDecimal calculatedStake,
    BigDecimal finalStake,
    boolean wouldBlock,
    StakeSizingBlockReason blockReason,
    StakeSizingDecisionReason decisionReason,
    String adjustmentSummary,
    Instant evaluatedAt,
    Instant createdAt,
    Instant lastEvaluatedAt,
    long observedCount
) {
    public StakeSizingShadowDecision {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required.");
        }
        if (recommendationId == null || recommendationId.isBlank()) {
            throw new IllegalArgumentException("recommendationId is required.");
        }
        if (policyName == null || riskProfile == null || source == null || decisionReason == null) {
            throw new IllegalArgumentException("policyName, riskProfile, source, and decisionReason are required.");
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt is required.");
        }
        id = id.strip();
        recommendationId = recommendationId.strip();
        canonicalKey = canonicalKey == null || canonicalKey.isBlank() ? null : canonicalKey.strip();
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        strategyName = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        adjustmentSummary = adjustmentSummary == null || adjustmentSummary.isBlank() ? "[]" : adjustmentSummary.strip();
        createdAt = createdAt == null ? evaluatedAt : createdAt;
        lastEvaluatedAt = lastEvaluatedAt == null ? evaluatedAt : lastEvaluatedAt;
        observedCount = Math.max(1, observedCount);
    }

    public StakeSizingShadowDecision withLatest(
        BigDecimal calculatedStake,
        BigDecimal finalStake,
        boolean wouldBlock,
        StakeSizingBlockReason blockReason,
        StakeSizingDecisionReason decisionReason,
        String adjustmentSummary,
        BigDecimal odds,
        Instant lastEvaluatedAt
    ) {
        return new StakeSizingShadowDecision(
            id,
            recommendationId,
            canonicalKey,
            policyName,
            riskProfile,
            source,
            selectionSide,
            odds,
            strategyName,
            baseStake,
            minStake,
            maxStake,
            bankroll,
            calculatedStake,
            finalStake,
            wouldBlock,
            blockReason,
            decisionReason,
            adjustmentSummary,
            evaluatedAt,
            createdAt,
            lastEvaluatedAt,
            observedCount + 1
        );
    }
}
