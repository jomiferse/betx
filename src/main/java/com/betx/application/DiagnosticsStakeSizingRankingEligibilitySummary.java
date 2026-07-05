package com.betx.application;

public record DiagnosticsStakeSizingRankingEligibilitySummary(
    long eligible,
    long insufficientSample,
    long noExposure,
    long allBlocked,
    long shadowOnly,
    long invalidData,
    long highRisk
) {
    public static DiagnosticsStakeSizingRankingEligibilitySummary empty() {
        return new DiagnosticsStakeSizingRankingEligibilitySummary(0, 0, 0, 0, 0, 0, 0);
    }
}
