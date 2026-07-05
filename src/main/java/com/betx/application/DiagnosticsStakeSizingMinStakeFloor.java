package com.betx.application;

import java.math.BigDecimal;

/** Measures how often min_stake turns a lower calculated shadow stake into the final stake. */
public record DiagnosticsStakeSizingMinStakeFloor(
    long floorAppliedCount,
    BigDecimal floorAppliedRate,
    BigDecimal avgCalculatedStakeBeforeFloor,
    BigDecimal avgFinalStakeAfterFloor,
    BigDecimal avgUplift,
    BigDecimal totalUplift
) {
    public static DiagnosticsStakeSizingMinStakeFloor empty() {
        return new DiagnosticsStakeSizingMinStakeFloor(0, null, null, null, null, BigDecimal.ZERO);
    }
}
