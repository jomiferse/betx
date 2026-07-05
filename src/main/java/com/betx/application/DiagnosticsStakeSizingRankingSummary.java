package com.betx.application;

public record DiagnosticsStakeSizingRankingSummary(
    long usefulRankingCandidates,
    long watchCandidates,
    long liveEligible,
    long excludedFromUsefulRankings,
    long noExposure,
    long allBlocked,
    long shadowOnly,
    long invalidData,
    long highRisk,
    long insufficientSample
) {
    public static DiagnosticsStakeSizingRankingSummary empty() {
        return new DiagnosticsStakeSizingRankingSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
