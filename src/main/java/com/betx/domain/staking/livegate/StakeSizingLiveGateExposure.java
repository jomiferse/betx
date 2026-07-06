package com.betx.domain.staking.livegate;

/** Exposure snapshot used by the pure live gate evaluator. */
public record StakeSizingLiveGateExposure(
    int openPositionsRemaining,
    boolean exposureSnapshotAvailable
) {
}
