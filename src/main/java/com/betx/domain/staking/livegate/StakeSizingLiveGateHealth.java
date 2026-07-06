package com.betx.domain.staking.livegate;

/** Shadow diagnostics health required before future live staking can be considered. */
public record StakeSizingLiveGateHealth(
    long shadowFailedCount,
    long duplicateLogicalKeysCount,
    long forbiddenLiveEventsCount,
    boolean shadowDiagnosticsFresh
) {
    public StakeSizingLiveGateHealth {
        shadowFailedCount = Math.max(0, shadowFailedCount);
        duplicateLogicalKeysCount = Math.max(0, duplicateLogicalKeysCount);
        forbiddenLiveEventsCount = Math.max(0, forbiddenLiveEventsCount);
    }
}
