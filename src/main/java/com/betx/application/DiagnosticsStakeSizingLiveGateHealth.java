package com.betx.application;

/** Read-only health counters used by stake sizing live gate diagnostics. */
public record DiagnosticsStakeSizingLiveGateHealth(
    long shadowFailedCount,
    long duplicateLogicalKeysCount,
    long forbiddenLiveEventsCount,
    boolean shadowDiagnosticsFresh
) {
}
