package com.betx.application;

/** Independent backtest result for one requested competition. */
public record BacktestLeagueReport(String competitionName, BacktestResult result, boolean hasData) {
    public BacktestLeagueReport {
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
    }
}
