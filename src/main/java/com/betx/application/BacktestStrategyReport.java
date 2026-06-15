package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Ranked aggregate performance for one research strategy. */
public record BacktestStrategyReport(
    String strategyId,
    int rank,
    BacktestResult result,
    List<BacktestMarketResult> marketResults
) {
    public BacktestStrategyReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
        marketResults = marketResults == null ? List.of() : List.copyOf(marketResults);
    }

    public BacktestStrategyReport(String strategyId, int rank, BacktestResult result) {
        this(strategyId, rank, result, List.of());
    }

    public BigDecimal grossProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return marketResults.stream()
            .map(BacktestMarketResult::grossPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal commissionPaid() {
        return marketResults.stream()
            .map(BacktestMarketResult::commissionPaid)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal netProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return marketResults.stream()
            .map(BacktestMarketResult::netPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal netRoiPercent() {
        BigDecimal totalStake = marketResults.stream()
            .map(BacktestMarketResult::totalStake)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (marketResults.isEmpty()) {
            totalStake = result.totalStaked();
        }
        if (totalStake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netProfitLoss()
            .divide(totalStake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal marketsWithMultipleSelectionsPercent() {
        if (marketResults.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long multiple = marketResults.stream()
            .filter(market -> market.selectedRunners() > 1)
            .count();
        return BigDecimal.valueOf(multiple)
            .divide(BigDecimal.valueOf(marketResults.size()), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
