package com.betx.domain.staking.livegate;

/** Current emergency-stop state for future live stake sizing. */
public record StakeSizingLiveGateKillSwitchState(
    boolean killSwitchActive,
    boolean stakeMismatchActive
) {
}
