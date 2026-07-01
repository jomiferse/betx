package com.betx.domain.staking;

/** Pure domain stake sizing policy. */
public interface StakeSizingPolicy {
    StakeSizingMode mode();

    StakeSizingDecision evaluate(StakeSizingContext context);
}
