package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Performance metrics for one backtest evaluation bucket. */
public record BacktestSegment(
    BacktestSegmentType type,
    String name,
    int trades,
    int wins,
    int losses,
    BigDecimal totalStaked,
    BigDecimal profitLoss,
    BigDecimal roiPercent,
    BigDecimal strikeRatePercent,
    BigDecimal maxDrawdown
) {
    public BacktestSegment {
        name = name == null || name.isBlank() ? "unknown" : name;
        totalStaked = totalStaked == null ? BigDecimal.ZERO : totalStaked;
        profitLoss = profitLoss == null ? BigDecimal.ZERO : profitLoss;
        roiPercent = roiPercent == null ? BigDecimal.ZERO : roiPercent;
        strikeRatePercent = strikeRatePercent == null ? BigDecimal.ZERO : strikeRatePercent;
        maxDrawdown = maxDrawdown == null ? BigDecimal.ZERO : maxDrawdown;
    }

    public static BacktestSegment from(BacktestSegmentType type, String name, List<BacktestTrade> trades) {
        List<BacktestTrade> orderedTrades = trades == null
            ? List.of()
            : trades.stream().sorted(Comparator.comparing(BacktestTrade::observedAt)).toList();
        int wins = (int) orderedTrades.stream().filter(trade -> trade.outcome() == BacktestOutcome.WIN).count();
        int losses = (int) orderedTrades.stream().filter(trade -> trade.outcome() == BacktestOutcome.LOSE).count();
        BigDecimal totalStaked = orderedTrades.stream()
            .map(BacktestTrade::stake)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitLoss = orderedTrades.stream()
            .map(BacktestTrade::profitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BacktestSegment(
            type,
            name,
            orderedTrades.size(),
            wins,
            losses,
            totalStaked,
            profitLoss,
            percent(profitLoss, totalStaked),
            strikeRate(wins, orderedTrades.size()),
            maxDrawdown(orderedTrades)
        );
    }

    private static BigDecimal strikeRate(int wins, int trades) {
        if (trades == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(wins)
            .divide(BigDecimal.valueOf(trades), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator
            .divide(denominator, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxDrawdown(List<BacktestTrade> trades) {
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BacktestTrade trade : trades) {
            equity = equity.add(trade.profitLoss());
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdown = peak.subtract(equity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }
}
