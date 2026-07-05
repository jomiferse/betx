package com.betx.application;

import java.util.List;

public record DiagnosticsStakeSizingScenarioSimulation(
    boolean enabled,
    boolean officiallyApplied,
    boolean shouldApplyLive,
    List<DiagnosticsStakeSizingScenario> scenarios,
    DiagnosticsStakeSizingScenarioRanking ranking,
    DiagnosticsStakeSizingRankingEligibilitySummary rankingEligibilitySummary,
    DiagnosticsStakeSizingScenarioRanking eligibleRankings,
    List<DiagnosticsStakeSizingScenarioExclusion> excludedFromEligibleRankings,
    DiagnosticsStakeSizingRankingSummary rankingSummary,
    DiagnosticsStakeSizingScenarioRanking watchRankings,
    DiagnosticsStakeSizingLiveEligibleRankings liveEligibleRankings,
    List<DiagnosticsStakeSizingScenarioExclusion> excludedFromUsefulRankings,
    List<DiagnosticsStakeSizingScenarioPolicyResult> watchCandidates
) {
    public DiagnosticsStakeSizingScenarioSimulation {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        ranking = ranking == null ? DiagnosticsStakeSizingScenarioRanking.empty() : ranking;
        rankingEligibilitySummary = rankingEligibilitySummary == null
            ? DiagnosticsStakeSizingRankingEligibilitySummary.empty()
            : rankingEligibilitySummary;
        eligibleRankings = eligibleRankings == null ? DiagnosticsStakeSizingScenarioRanking.empty() : eligibleRankings;
        excludedFromEligibleRankings = excludedFromEligibleRankings == null
            ? List.of()
            : List.copyOf(excludedFromEligibleRankings);
        rankingSummary = rankingSummary == null ? DiagnosticsStakeSizingRankingSummary.empty() : rankingSummary;
        watchRankings = watchRankings == null ? DiagnosticsStakeSizingScenarioRanking.empty() : watchRankings;
        liveEligibleRankings = liveEligibleRankings == null
            ? DiagnosticsStakeSizingLiveEligibleRankings.empty()
            : liveEligibleRankings;
        excludedFromUsefulRankings = excludedFromUsefulRankings == null
            ? List.of()
            : List.copyOf(excludedFromUsefulRankings);
        watchCandidates = watchCandidates == null ? List.of() : List.copyOf(watchCandidates);
    }

    public static DiagnosticsStakeSizingScenarioSimulation empty() {
        return new DiagnosticsStakeSizingScenarioSimulation(
            false,
            false,
            false,
            List.of(),
            DiagnosticsStakeSizingScenarioRanking.empty(),
            DiagnosticsStakeSizingRankingEligibilitySummary.empty(),
            DiagnosticsStakeSizingScenarioRanking.empty(),
            List.of(),
            DiagnosticsStakeSizingRankingSummary.empty(),
            DiagnosticsStakeSizingScenarioRanking.empty(),
            DiagnosticsStakeSizingLiveEligibleRankings.empty(),
            List.of(),
            List.of()
        );
    }
}
