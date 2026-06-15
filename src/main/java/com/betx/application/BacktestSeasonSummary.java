package com.betx.application;

import java.math.BigDecimal;

/** Summary statistics derived from independent season-level strategy results. */
public record BacktestSeasonSummary(
    String strategyId,
    int evaluatedSeasons,
    int profitableSeasons,
    int losingSeasons,
    BigDecimal meanNetRoiPercent,
    BigDecimal medianNetRoiPercent,
    BigDecimal worstNetRoiPercent,
    BigDecimal bestNetRoiPercent,
    BigDecimal totalNetRoiPercent,
    BigDecimal meanRoiPercent,
    BigDecimal medianRoiPercent,
    BigDecimal worstSeasonRoiPercent
) {
    public BacktestSeasonSummary {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        meanNetRoiPercent = meanNetRoiPercent == null ? BigDecimal.ZERO : meanNetRoiPercent;
        medianNetRoiPercent = medianNetRoiPercent == null ? BigDecimal.ZERO : medianNetRoiPercent;
        worstNetRoiPercent = worstNetRoiPercent == null ? BigDecimal.ZERO : worstNetRoiPercent;
        bestNetRoiPercent = bestNetRoiPercent == null ? BigDecimal.ZERO : bestNetRoiPercent;
        totalNetRoiPercent = totalNetRoiPercent == null ? BigDecimal.ZERO : totalNetRoiPercent;
        meanRoiPercent = meanRoiPercent == null ? meanNetRoiPercent : meanRoiPercent;
        medianRoiPercent = medianRoiPercent == null ? medianNetRoiPercent : medianRoiPercent;
        worstSeasonRoiPercent = worstSeasonRoiPercent == null ? worstNetRoiPercent : worstSeasonRoiPercent;
    }

    public BacktestSeasonSummary(
        String strategyId,
        BigDecimal meanRoiPercent,
        BigDecimal medianRoiPercent,
        int profitableSeasons,
        BigDecimal worstSeasonRoiPercent
    ) {
        this(
            strategyId,
            0,
            profitableSeasons,
            0,
            meanRoiPercent,
            medianRoiPercent,
            worstSeasonRoiPercent,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            meanRoiPercent,
            medianRoiPercent,
            worstSeasonRoiPercent
        );
    }
}
