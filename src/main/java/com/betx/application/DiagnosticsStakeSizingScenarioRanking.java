package com.betx.application;

public record DiagnosticsStakeSizingScenarioRanking(
    String bestSimulatedRoi,
    String bestSimulatedPnl,
    String lowestSimulatedDrawdown,
    String bestRiskAdjustedScenario,
    String highestRiskScenario,
    String mostAffectedByMinStakeFloor,
    String warning,
    boolean shouldApplyLive
) {
    public static DiagnosticsStakeSizingScenarioRanking empty() {
        return new DiagnosticsStakeSizingScenarioRanking(null, null, null, null, null, null, null, false);
    }
}
