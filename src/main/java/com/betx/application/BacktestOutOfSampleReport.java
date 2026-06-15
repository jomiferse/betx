package com.betx.application;

/** Diagnostic period result for development, validation, or untouched test seasons. */
public record BacktestOutOfSampleReport(
    String strategyId,
    String period,
    String startSeason,
    String endSeason,
    BacktestResult result,
    java.util.List<BacktestMarketResult> marketResults
) {
    public BacktestOutOfSampleReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        period = period == null || period.isBlank() ? "unknown" : period;
        startSeason = startSeason == null || startSeason.isBlank() ? "unknown" : startSeason;
        endSeason = endSeason == null || endSeason.isBlank() ? "unknown" : endSeason;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
        marketResults = marketResults == null ? java.util.List.of() : java.util.List.copyOf(marketResults);
    }

    public BacktestOutOfSampleReport(String strategyId, String period, String startSeason, String endSeason, BacktestResult result) {
        this(strategyId, period, startSeason, endSeason, result, java.util.List.of());
    }
}
