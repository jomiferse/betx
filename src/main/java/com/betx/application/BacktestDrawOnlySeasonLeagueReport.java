package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Focused validation metrics for value-football-draw-only by league and season. */
public record BacktestDrawOnlySeasonLeagueReport(
    String league,
    String season,
    List<BacktestTrade> tradeList
) {
    public BacktestDrawOnlySeasonLeagueReport {
        league = league == null || league.isBlank() ? "unknown" : league;
        season = season == null || season.isBlank() ? "unknown" : season;
        tradeList = tradeList == null
            ? List.of()
            : tradeList.stream().sorted(Comparator.comparing(BacktestTrade::observedAt)).toList();
    }

    public int trades() {
        return tradeList.size();
    }

    public int wins() {
        return (int) tradeList.stream().filter(trade -> trade.outcome() == BacktestOutcome.WIN).count();
    }

    public BigDecimal averageOdds() {
        if (tradeList.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return tradeList.stream()
            .map(BacktestTrade::odds)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(tradeList.size()), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal grossProfitLoss() {
        return tradeList.stream().map(BacktestTrade::profitLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal roiPercent() {
        BigDecimal stake = tradeList.stream().map(BacktestTrade::stake).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return grossProfitLoss()
            .divide(stake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal maxDrawdown() {
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BacktestTrade trade : tradeList) {
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

    public int longestLosingStreak() {
        int longest = 0;
        int current = 0;
        for (BacktestTrade trade : tradeList) {
            if (trade.profitLoss().compareTo(BigDecimal.ZERO) < 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }
}
