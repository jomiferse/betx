package com.betx.application;

/** Strategy diagnostics for league, runner type, and odds-band groupings. */
public record BacktestBreakdownReport(
    String kind,
    String strategyId,
    String league,
    BacktestRunnerType runnerType,
    String oddsBand,
    BacktestResult result
) {
    public BacktestBreakdownReport {
        kind = kind == null || kind.isBlank() ? "unknown" : kind;
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        league = league == null || league.isBlank() ? "all" : league;
        runnerType = runnerType == null ? BacktestRunnerType.UNKNOWN : runnerType;
        oddsBand = oddsBand == null || oddsBand.isBlank() ? "all" : oddsBand;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
    }
}
