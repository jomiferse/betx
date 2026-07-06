package com.betx.domain.staking.livegate;

import java.math.BigDecimal;

/** Candidate stake values produced before the live gate decides whether they are eligible. */
public record StakeSizingLiveGateStake(
    BigDecimal finalStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    BigDecimal bankroll,
    boolean wouldBlock
) {
    public StakeSizingLiveGateStake {
        finalStake = valueOrZero(finalStake);
        minStake = valueOrZero(minStake);
        maxStake = valueOrZero(maxStake);
        bankroll = valueOrZero(bankroll);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
