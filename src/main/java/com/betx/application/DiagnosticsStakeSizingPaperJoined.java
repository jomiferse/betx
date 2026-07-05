package com.betx.application;

import java.math.BigDecimal;

/** Paper-trade join and simulation metrics for one shadow stake sizing policy/profile. */
public record DiagnosticsStakeSizingPaperJoined(
    long paperJoinedTrades,
    long paperSettledJoined,
    long paperOpenExecuted,
    long paperExecutionFailed,
    BigDecimal baselinePnl,
    BigDecimal simulatedPnl,
    BigDecimal simulatedRoi,
    String sampleWarning
) {
    public static DiagnosticsStakeSizingPaperJoined empty() {
        return new DiagnosticsStakeSizingPaperJoined(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
}
