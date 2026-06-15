package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Closing-line-value metrics grouped by paper-trade segment. */
public record BacktestClvBreakdownReport(
    String kind,
    String name,
    int trades,
    BigDecimal averageClv,
    BigDecimal medianClv,
    BigDecimal positiveClvPercent
) {
    public static BacktestClvBreakdownReport from(String kind, String name, List<BacktestPaperTrade> trades) {
        BacktestClvSummary summary = BacktestClvSummary.from(trades);
        return new BacktestClvBreakdownReport(
            kind,
            name == null || name.isBlank() ? "unknown" : name,
            summary.trades(),
            summary.averageClv(),
            summary.medianClv(),
            summary.positiveClvPercent()
        );
    }

    public BacktestClvBreakdownReport {
        averageClv = averageClv == null ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : averageClv;
        medianClv = medianClv == null ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : medianClv;
        positiveClvPercent = positiveClvPercent == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : positiveClvPercent;
    }
}
