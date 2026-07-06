package com.betx.domain.staking.livegate;

/** Prospective joined sample available for the candidate policy. */
public record StakeSizingLiveGateSample(int realSettledJoined) {
    public StakeSizingLiveGateSample {
        realSettledJoined = Math.max(0, realSettledJoined);
    }
}
