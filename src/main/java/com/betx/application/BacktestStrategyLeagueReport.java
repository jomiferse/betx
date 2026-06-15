package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** League-level aggregate performance for one research strategy. */
public record BacktestStrategyLeagueReport(
    String strategyId,
    String competitionName,
    BacktestResult result,
    List<BacktestMarketResult> marketResults
) {
    public BacktestStrategyLeagueReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        result = result == null ? BacktestResult.from(0, 0, java.util.List.of()) : result;
        marketResults = marketResults == null ? List.of() : List.copyOf(marketResults);
    }

    public BacktestStrategyLeagueReport(String strategyId, String competitionName, BacktestResult result) {
        this(strategyId, competitionName, result, List.of());
    }

    public BigDecimal grossProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return marketResults.stream().map(BacktestMarketResult::grossPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal commissionPaid() {
        return marketResults.stream().map(BacktestMarketResult::commissionPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal netProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return marketResults.stream().map(BacktestMarketResult::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal netRoiPercent() {
        BigDecimal totalStake = marketResults.isEmpty()
            ? result.totalStaked()
            : marketResults.stream().map(BacktestMarketResult::totalStake).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalStake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netProfitLoss()
            .divide(totalStake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
