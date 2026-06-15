package com.betx.application;

import java.math.BigDecimal;

/** Rolling settled paper-trade metrics for a fixed window size. */
public record BacktestRollingPaperWindow(
    int windowSize,
    int trades,
    BigDecimal roiPercent,
    BigDecimal averageClv,
    BigDecimal maxDrawdown,
    int longestLosingStreak
) {
}
