package com.betx.application;

import java.math.BigDecimal;

/** Backtest result for one odds-movement threshold candidate. */
public record BacktestSensitivityReport(String competitionName, BigDecimal threshold, BacktestResult result) {
    public BacktestSensitivityReport {
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        threshold = threshold == null ? BigDecimal.ZERO : threshold;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
    }
}
