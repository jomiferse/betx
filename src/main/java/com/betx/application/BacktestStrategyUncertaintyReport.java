package com.betx.application;

import java.math.BigDecimal;

/** Statistical diagnostics for a strategy's market-settled trade sample. */
public record BacktestStrategyUncertaintyReport(
    String strategyId,
    BigDecimal bootstrapNetRoiLower95,
    BigDecimal bootstrapNetRoiUpper95,
    int longestLosingStreak,
    BigDecimal profitFactor,
    BigDecimal averageOdds,
    BigDecimal expectedValuePerTrade
) {
    public BacktestStrategyUncertaintyReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        bootstrapNetRoiLower95 = bootstrapNetRoiLower95 == null ? BigDecimal.ZERO : bootstrapNetRoiLower95;
        bootstrapNetRoiUpper95 = bootstrapNetRoiUpper95 == null ? BigDecimal.ZERO : bootstrapNetRoiUpper95;
        profitFactor = profitFactor == null ? BigDecimal.ZERO : profitFactor;
        averageOdds = averageOdds == null ? BigDecimal.ZERO : averageOdds;
        expectedValuePerTrade = expectedValuePerTrade == null ? BigDecimal.ZERO : expectedValuePerTrade;
    }
}
