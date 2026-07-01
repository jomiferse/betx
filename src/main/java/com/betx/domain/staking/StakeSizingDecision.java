package com.betx.domain.staking;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable output of the pure stake sizing engine. */
public record StakeSizingDecision(
    String recommendationId,
    String canonicalKey,
    StakeSizingMode mode,
    StakeSizingRiskProfile riskProfile,
    BigDecimal baseStake,
    BigDecimal calculatedStake,
    BigDecimal finalStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    BigDecimal odds,
    SelectionSide selectionSide,
    boolean wouldBlock,
    StakeSizingBlockReason blockReason,
    StakeSizingDecisionReason decisionReason,
    List<StakeSizingAdjustment> adjustments,
    StakeSizingSource source,
    Instant createdAt,
    Instant evaluatedAt,
    boolean shadowOnly
) {
    public StakeSizingDecision {
        riskProfile = riskProfile == null ? StakeSizingRiskProfile.BALANCED : riskProfile;
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
        source = source == null ? StakeSizingSource.SHADOW : source;
        createdAt = createdAt == null ? evaluatedAt : createdAt;
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }
}
