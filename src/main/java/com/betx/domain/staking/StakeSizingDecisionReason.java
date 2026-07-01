package com.betx.domain.staking;

/** Human-auditable reason for the stake sizing result. */
public enum StakeSizingDecisionReason {
    BASE_STAKE,
    CONFIDENCE_SCORE,
    CONFIDENCE_NOT_AVAILABLE,
    RISK_ADJUSTED,
    FRACTIONAL_KELLY,
    PROBABILITY_NOT_AVAILABLE
}
