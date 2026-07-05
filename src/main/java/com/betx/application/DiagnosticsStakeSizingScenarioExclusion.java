package com.betx.application;

public record DiagnosticsStakeSizingScenarioExclusion(
    String scenarioName,
    String policyName,
    String riskProfile,
    DiagnosticsStakeSizingRankingEligibility rankingEligibility,
    String reason
) {
}
