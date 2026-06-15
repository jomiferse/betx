package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Market-level settlement for one strategy, after commission. */
public record BacktestMarketResult(
    String strategyId,
    String exchange,
    String marketId,
    String eventName,
    String marketName,
    String competitionName,
    String season,
    String oddsSource,
    int selectedRunners,
    BigDecimal totalStake,
    BigDecimal grossPnl,
    BigDecimal commissionPaid,
    BigDecimal netPnl,
    BigDecimal maximumExposure,
    List<BacktestTrade> trades
) {
    public BacktestMarketResult {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        exchange = exchange == null || exchange.isBlank() ? "unknown" : exchange;
        marketId = marketId == null || marketId.isBlank() ? "unknown" : marketId;
        eventName = eventName == null || eventName.isBlank() ? "unknown" : eventName;
        marketName = marketName == null || marketName.isBlank() ? "unknown" : marketName;
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        season = season == null || season.isBlank() ? "unknown" : season;
        oddsSource = oddsSource == null || oddsSource.isBlank() ? "unknown" : oddsSource;
        totalStake = totalStake == null ? BigDecimal.ZERO : totalStake;
        grossPnl = grossPnl == null ? BigDecimal.ZERO : grossPnl;
        commissionPaid = commissionPaid == null ? BigDecimal.ZERO : commissionPaid;
        netPnl = netPnl == null ? BigDecimal.ZERO : netPnl;
        maximumExposure = maximumExposure == null ? BigDecimal.ZERO : maximumExposure;
        trades = trades == null ? List.of() : List.copyOf(trades);
    }

    public static BacktestMarketResult from(String strategyId, List<BacktestTrade> trades, BigDecimal commissionRate) {
        List<BacktestTrade> safeTrades = trades == null ? List.of() : List.copyOf(trades);
        BacktestTrade first = safeTrades.isEmpty() ? null : safeTrades.getFirst();
        BigDecimal totalStake = safeTrades.stream()
            .map(BacktestTrade::stake)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossPnl = safeTrades.stream()
            .map(BacktestTrade::profitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = grossPnl.compareTo(BigDecimal.ZERO) > 0
            ? grossPnl.multiply(normalizedCommissionRate(commissionRate)).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        return new BacktestMarketResult(
            strategyId,
            first == null ? "unknown" : first.exchange(),
            first == null ? "unknown" : first.marketId(),
            first == null ? "unknown" : first.eventName(),
            first == null ? "unknown" : first.marketName(),
            first == null ? "unknown" : first.competitionName(),
            first == null ? "unknown" : first.season(),
            first == null ? "unknown" : first.oddsSource(),
            safeTrades.size(),
            totalStake,
            grossPnl,
            commission,
            grossPnl.subtract(commission),
            totalStake,
            safeTrades
        );
    }

    public static BacktestMarketResult empty(String strategyId, BacktestInputRow row) {
        return new BacktestMarketResult(
            strategyId,
            row.exchange(),
            row.marketId(),
            row.eventName(),
            row.marketName(),
            row.competitionName(),
            row.season(),
            row.oddsSource(),
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of()
        );
    }

    public BigDecimal netRoiPercent() {
        if (totalStake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netPnl.divide(totalStake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizedCommissionRate(BigDecimal commissionRate) {
        return commissionRate == null ? BigDecimal.ZERO : commissionRate;
    }
}
