package com.betx.application;

import java.math.BigDecimal;

/** Train-on-season-N and evaluate-on-season-N+1 backtest result. */
public record BacktestWalkForwardValidation(
    String competitionName,
    BacktestWalkForwardStatus status,
    Integer trainSeason,
    Integer evaluationSeason,
    BigDecimal selectedThreshold,
    BacktestResult trainResult,
    BacktestResult evaluationResult
) {
    public BacktestWalkForwardValidation {
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        status = status == null ? BacktestWalkForwardStatus.INSUFFICIENT_SEASONS : status;
        trainResult = trainResult == null ? BacktestResult.from(0, 0, java.util.List.of()) : trainResult;
        evaluationResult = evaluationResult == null ? BacktestResult.from(0, 0, java.util.List.of()) : evaluationResult;
    }

    public static BacktestWalkForwardValidation insufficientSeasons(String competitionName) {
        return new BacktestWalkForwardValidation(
            competitionName,
            BacktestWalkForwardStatus.INSUFFICIENT_SEASONS,
            null,
            null,
            null,
            BacktestResult.from(0, 0, java.util.List.of()),
            BacktestResult.from(0, 0, java.util.List.of())
        );
    }
}
