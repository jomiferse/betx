package com.betx.domain.staking;

/** Reason a stake sizing decision would block in shadow evaluation. */
public enum StakeSizingBlockReason {
    NOT_AVAILABLE,
    NEGATIVE_OR_ZERO_KELLY,
    BLOCKED_BY_RISK_BUDGET,
    BELOW_MIN_STAKE_AFTER_LIMITS
}
