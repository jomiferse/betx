package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Independent market-settled strategy result for one explicit season. */
public record BacktestSeasonReport(
    String strategyId,
    String season,
    BacktestResult result,
    List<BacktestMarketResult> marketResults
) {
    public BacktestSeasonReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        season = season == null || season.isBlank() ? "unknown" : season;
        result = result == null ? BacktestResult.from(0, 0, List.of()) : result;
        marketResults = marketResults == null ? List.of() : List.copyOf(marketResults);
    }

    public BacktestSeasonReport(String strategyId, String season, BacktestResult result) {
        this(strategyId, season, result, List.of());
    }

    public int markets() {
        return marketResults.size();
    }

    public int trades() {
        return result.trades().size();
    }

    public BigDecimal grossProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return sum(BacktestMarketResult::grossPnl);
    }

    public BigDecimal commissionPaid() {
        return sum(BacktestMarketResult::commissionPaid);
    }

    public BigDecimal netProfitLoss() {
        if (marketResults.isEmpty()) {
            return result.profitLoss();
        }
        return sum(BacktestMarketResult::netPnl);
    }

    public BigDecimal grossRoiPercent() {
        return percent(grossProfitLoss(), totalStake());
    }

    public BigDecimal netRoiPercent() {
        return percent(netProfitLoss(), totalStake());
    }

    public BigDecimal grossMaxDrawdown() {
        return drawdown(marketResults.stream().map(BacktestMarketResult::grossPnl).toList());
    }

    public BigDecimal netMaxDrawdown() {
        return drawdown(marketResults.stream().map(BacktestMarketResult::netPnl).toList());
    }

    public BigDecimal strikeRatePercent() {
        return result.strikeRatePercent();
    }

    private BigDecimal totalStake() {
        if (marketResults.isEmpty()) {
            return result.totalStaked();
        }
        return sum(BacktestMarketResult::totalStake);
    }

    private BigDecimal sum(java.util.function.Function<BacktestMarketResult, BigDecimal> mapper) {
        return marketResults.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal drawdown(List<BigDecimal> pnlSeries) {
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal pnl : pnlSeries) {
            equity = equity.add(pnl == null ? BigDecimal.ZERO : pnl);
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
