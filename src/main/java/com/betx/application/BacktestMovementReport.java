package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Performance grouped by opening-to-closing price movement bucket. */
public record BacktestMovementReport(
    String strategyId,
    String movementBucket,
    List<BacktestTrade> tradeList
) {
    public BacktestMovementReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        movementBucket = movementBucket == null || movementBucket.isBlank() ? "unknown" : movementBucket;
        tradeList = tradeList == null ? List.of() : List.copyOf(tradeList);
    }

    public int trades() {
        return tradeList.size();
    }

    public BigDecimal pnl() {
        return tradeList.stream().map(BacktestTrade::profitLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal roiPercent() {
        BigDecimal stake = tradeList.stream().map(BacktestTrade::stake).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return pnl().divide(stake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
