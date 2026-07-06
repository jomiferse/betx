package com.betx.application;

/** Diagnostics-only exposure snapshot for future stake sizing live gate checks. */
public record DiagnosticsStakeSizingLiveGateExposure(
    int openPositionsRemaining,
    boolean exposureSnapshotAvailable
) {
    public String status() {
        return exposureSnapshotAvailable ? "AVAILABLE" : "MISSING";
    }
}
