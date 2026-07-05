package com.betx.application;

import java.math.BigDecimal;

/** Real-bet join and simulation metrics for one shadow stake sizing policy/profile. */
public record DiagnosticsStakeSizingRealJoined(
    long realJoinedBets,
    long realSettledJoined,
    long realOpenJoined,
    long wins,
    long losses,
    long voidsCancelled,
    BigDecimal baselineRealTurnover,
    BigDecimal baselineRealPnl,
    BigDecimal baselineRealRoi,
    BigDecimal simulatedTurnover,
    BigDecimal simulatedPnl,
    BigDecimal simulatedRoi,
    BigDecimal deltaPnl,
    BigDecimal deltaRoi,
    BigDecimal avgStakeMultiplier,
    BigDecimal maxSimulatedStake,
    long wouldBlockCount,
    BigDecimal wouldBlockRate,
    BigDecimal currentSimulatedDrawdown,
    BigDecimal maxSimulatedDrawdown,
    BigDecimal baselineDrawdown,
    BigDecimal deltaDrawdown,
    DiagnosticsStakeSizingPolicyStatus status,
    String sampleWarning,
    String stakeFallbackWarning
) {
    public DiagnosticsStakeSizingRealJoined {
        status = status == null ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE : status;
    }

    public static DiagnosticsStakeSizingRealJoined empty() {
        return new DiagnosticsStakeSizingRealJoined(
            0,
            0,
            0,
            0,
            0,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            null,
            null,
            null,
            0,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE,
            "Insufficient settled joined sample.",
            null
        );
    }
}
