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
    DiagnosticsStakeSizingRankingEligibility rankingEligibility,
    boolean hasExposure,
    boolean allBlocked,
    boolean shadowOnly,
    boolean validData,
    boolean sufficientSample,
    boolean highRisk,
    boolean eligibleForUsefulRanking,
    boolean watchCandidate,
    boolean eligibleForLive,
    boolean shouldApplyLive
) {
    public DiagnosticsStakeSizingScenarioPolicyResult {
        status = status == null ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE : status;
        rankingEligibility = rankingEligibility == null
            ? DiagnosticsStakeSizingRankingEligibility.INVALID_DATA
            : rankingEligibility;
    }

    public boolean isAllBlocked() {
        return allBlocked;
    }

    public boolean isShadowOnly() {
        return shadowOnly;
    }

    public boolean hasValidData() {
        return validData;
    }

    public boolean hasSufficientSample() {
        return sufficientSample;
    }

    public boolean isHighRisk() {
        return highRisk;
    }
}
