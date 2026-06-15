package com.betx.application;

import java.math.BigDecimal;

/** Decision emitted by a research backtest strategy for one simulated entry. */
public record BacktestStrategyDecision(
    String reason,
    String confidenceLabel,
    BigDecimal oddsMovementPercent
) {
    public BacktestStrategyDecision {
        reason = reason == null || reason.isBlank() ? "research_strategy" : reason;
        confidenceLabel = confidenceLabel == null || confidenceLabel.isBlank() ? "Benchmark" : confidenceLabel;
    }
}
