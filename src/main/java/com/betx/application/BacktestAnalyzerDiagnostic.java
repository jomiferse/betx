package com.betx.application;

/** Aggregated analyzer rejection reason for a strategy and odds source. */
public record BacktestAnalyzerDiagnostic(
    String strategyId,
    String oddsSource,
    String reason,
    int count
) {
    public BacktestAnalyzerDiagnostic {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        oddsSource = oddsSource == null || oddsSource.isBlank() ? "unknown" : oddsSource;
        reason = reason == null || reason.isBlank() ? "unknown" : reason;
    }
}
