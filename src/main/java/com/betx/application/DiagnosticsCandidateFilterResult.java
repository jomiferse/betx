package com.betx.application;

import java.math.BigDecimal;

/** Diagnostics-only result for a simulated candidate filter. */
public record DiagnosticsCandidateFilterResult(
    String filterName,
    String scope,
    long baselineBets,
    long includedBets,
    long excludedBets,
    BigDecimal includedTurnover,
    BigDecimal excludedTurnover,
    BigDecimal includedWinRate,
    BigDecimal includedAvgOdds,
    BigDecimal includedNetPnl,
    BigDecimal includedRoi,
    BigDecimal includedMaxDrawdown,
    BigDecimal baselineNetPnl,
    BigDecimal baselineRoi,
    BigDecimal baselineMaxDrawdown,
    BigDecimal deltaPnl,
    BigDecimal deltaRoi,
    BigDecimal volumeRetentionPct,
    DiagnosticsCandidateFilterStatus status,
    String riskNote,
    String sampleWarning
) {
}
