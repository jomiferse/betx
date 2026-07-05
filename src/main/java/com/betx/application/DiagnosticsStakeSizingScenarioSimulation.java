package com.betx.application;

import java.util.List;

public record DiagnosticsStakeSizingScenarioSimulation(
    boolean enabled,
    boolean officiallyApplied,
    boolean shouldApplyLive,
    List<DiagnosticsStakeSizingScenario> scenarios,
    DiagnosticsStakeSizingScenarioRanking ranking
) {
    public DiagnosticsStakeSizingScenarioSimulation {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        ranking = ranking == null ? DiagnosticsStakeSizingScenarioRanking.empty() : ranking;
    }

    public static DiagnosticsStakeSizingScenarioSimulation empty() {
        return new DiagnosticsStakeSizingScenarioSimulation(false, false, false, List.of(), DiagnosticsStakeSizingScenarioRanking.empty());
    }
}
