package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Execution-degradation scenario for the focused draw-only validation. */
public record BacktestSlippageReport(
    String strategyId,
    BigDecimal slippageRate,
    int trades,
    BigDecimal grossPnl,
    BigDecimal netPnl,
    BigDecimal netRoiPercent
) {
    public BacktestSlippageReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        slippageRate = slippageRate == null ? BigDecimal.ZERO : slippageRate.stripTrailingZeros();
        grossPnl = grossPnl == null ? BigDecimal.ZERO : grossPnl;
        netPnl = netPnl == null ? BigDecimal.ZERO : netPnl;
        netRoiPercent = netRoiPercent == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : netRoiPercent;
    }
}
