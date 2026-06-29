package com.betx.application;

import java.math.BigDecimal;

/** Read-only performance metrics for one diagnostics strategy segment. */
public record DiagnosticsStrategyPerformanceSegment(
    String name,
    long bets,
    long settled,
    long open,
    long wins,
    long losses,
    long voids,
    long cancelled,
    BigDecimal strikeRate,
    BigDecimal averageOdds,
    BigDecimal averageStake,
    BigDecimal turnover,
    BigDecimal grossPnl,
    BigDecimal commission,
    BigDecimal netPnl,
    BigDecimal roi,
    BigDecimal maxDrawdown,
    BigDecimal currentDrawdown,
    BigDecimal profitFactor,
    BigDecimal averageWin,
    BigDecimal averageLoss,
    BigDecimal expectedBreakEvenOdds
) {
    public static DiagnosticsStrategyPerformanceSegment empty(String name) {
        return new DiagnosticsStrategyPerformanceSegment(
            name,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
