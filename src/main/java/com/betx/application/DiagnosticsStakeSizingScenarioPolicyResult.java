package com.betx.application;

import java.math.BigDecimal;

public record DiagnosticsStakeSizingScenarioPolicyResult(
    String scenarioName,
    String policyName,
    String riskProfile,
    BigDecimal baseStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    long recommendationsEvaluated,
    long realJoinedBets,
    long realSettledJoined,
    long realOpenJoined,
    long wins,
    long losses,
    BigDecimal baselineRealTurnover,
    BigDecimal baselineRealPnl,
    BigDecimal baselineRealRoi,
    BigDecimal simulatedTurnover,
    BigDecimal simulatedPnl,
    BigDecimal simulatedRoi,
    BigDecimal deltaPnl,
    BigDecimal deltaRoi,
    BigDecimal avgCalculatedStake,
    BigDecimal avgFinalStake,
    BigDecimal minFinalStake,
    BigDecimal maxFinalStake,
    BigDecimal avgStakeMultiplier,
    BigDecimal maxSimulatedExposure,
    BigDecimal simulatedMaxDrawdown,
    BigDecimal simulatedCurrentDrawdown,
    BigDecimal baselineMaxDrawdown,
    BigDecimal deltaDrawdown,
    long wouldBlockCount,
    BigDecimal wouldBlockRate,
    long minStakeFloorAppliedCount,
    BigDecimal minStakeFloorAppliedRate,
    BigDecimal avgFloorUplift,
    BigDecimal totalFloorUplift,
    long fallbackRequestedStakeCount,
    long invalidStakeExcludedCount,
    DiagnosticsStakeSizingPolicyStatus status,
    String warning,
    boolean shouldApplyLive
) {
    public DiagnosticsStakeSizingScenarioPolicyResult {
        status = status == null ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE : status;
    }
}
