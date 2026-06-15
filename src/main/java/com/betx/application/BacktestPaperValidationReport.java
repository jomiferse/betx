package com.betx.application;

import java.math.BigDecimal;

/** Evidence gate for prospective paper-trading validation. */
public record BacktestPaperValidationReport(
    BacktestPaperValidationStatus status,
    BacktestClvStatus clvStatus,
    int settledTrades,
    BigDecimal medianClv,
    BigDecimal theoreticalRoiPercent,
    BigDecimal executableRoiPercent,
    BigDecimal closingOddsRoiPercent,
    BigDecimal executionLossPercentagePoints
) {
}
