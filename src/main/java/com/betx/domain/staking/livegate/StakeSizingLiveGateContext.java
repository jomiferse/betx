package com.betx.domain.staking.livegate;

/** Immutable input bundle for the pure stake sizing live gate evaluator. */
public record StakeSizingLiveGateContext(
    StakeSizingLiveGateConfig config,
    StakeSizingLiveGatePolicy policy,
    StakeSizingLiveGateSample sample,
    StakeSizingLiveGateHealth health,
    StakeSizingLiveGateBudget budget,
    StakeSizingLiveGateExposure exposure,
    StakeSizingLiveGateKillSwitchState killSwitchState,
    StakeSizingLiveGateStake stake
) {
    public StakeSizingLiveGateContext {
        config = config == null
            ? new StakeSizingLiveGateConfig(false, false, true, null, null, null, null, 100, null, null, true, false, null)
            : config;
        policy = policy == null ? new StakeSizingLiveGatePolicy(null, null) : policy;
        sample = sample == null ? new StakeSizingLiveGateSample(0) : sample;
        health = health == null ? new StakeSizingLiveGateHealth(0, 0, 0, false) : health;
        budget = budget == null ? new StakeSizingLiveGateBudget(null, null, null, null, false) : budget;
        exposure = exposure == null ? new StakeSizingLiveGateExposure(0, false) : exposure;
        killSwitchState = killSwitchState == null ? new StakeSizingLiveGateKillSwitchState(false, false) : killSwitchState;
        stake = stake == null ? new StakeSizingLiveGateStake(null, null, null, null, true) : stake;
    }
}
