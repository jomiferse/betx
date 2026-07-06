package com.betx.application;

import java.math.BigDecimal;

/** Diagnostics-only budget snapshot for future stake sizing live gate checks. */
public record DiagnosticsStakeSizingLiveGateBudget(
    BigDecimal dailyLossBudgetRemaining,
    BigDecimal totalExposureRemaining,
    BigDecimal marketExposureRemaining,
    boolean budgetSnapshotAvailable
) {
    public String status() {
        return budgetSnapshotAvailable ? "AVAILABLE" : "MISSING";
    }
}
