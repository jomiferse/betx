package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read-only summary of runtime dry-run live gate validation logs. */
public record DiagnosticsStakeSizingLiveGateDryRun(
    boolean enabled,
    long evaluationsTotal,
    long failedTotal,
    Instant lastEvaluatedAt,
    String lastGateStatus,
    List<String> lastReasons,
    BigDecimal lastRepresentativeFinalStake,
    BigDecimal fixedStake,
    BigDecimal fallbackStake,
    long liveAppliedEvents,
    long orderStakeChangedEvents
) {
    public DiagnosticsStakeSizingLiveGateDryRun {
        lastReasons = lastReasons == null ? List.of() : List.copyOf(lastReasons);
    }

    public static DiagnosticsStakeSizingLiveGateDryRun empty() {
        return new DiagnosticsStakeSizingLiveGateDryRun(
            true,
            0,
            0,
            null,
            null,
            List.of(),
            null,
            new BigDecimal("1.00"),
            new BigDecimal("1.00"),
            0,
            0
        );
    }
}
