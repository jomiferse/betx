package com.betx.application;

public record DiagnosticsStakeSizingLiveEligibleRankings(
    boolean available,
    String reason,
    DiagnosticsStakeSizingScenarioRanking rankings
) {
    public DiagnosticsStakeSizingLiveEligibleRankings {
        rankings = rankings == null ? DiagnosticsStakeSizingScenarioRanking.empty() : rankings;
    }

    public static DiagnosticsStakeSizingLiveEligibleRankings empty() {
        return new DiagnosticsStakeSizingLiveEligibleRankings(false, "NO_LIVE_ELIGIBLE_RESULTS", DiagnosticsStakeSizingScenarioRanking.empty());
    }
}
