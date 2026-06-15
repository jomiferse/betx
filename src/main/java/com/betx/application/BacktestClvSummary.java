package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Aggregate closing-line-value metrics for paper trades. */
public record BacktestClvSummary(
    BacktestClvStatus status,
    int trades,
    BigDecimal averageClv,
    BigDecimal medianClv,
    BigDecimal positiveClvPercent
) {
    public static BacktestClvSummary from(List<BacktestPaperTrade> trades) {
        return from(BacktestClvStatus.VALID_PROSPECTIVE, trades);
    }

    public static BacktestClvSummary unavailable(List<BacktestPaperTrade> trades) {
        return from(BacktestClvStatus.NOT_AVAILABLE, trades);
    }

    public static BacktestClvSummary from(BacktestClvStatus status, List<BacktestPaperTrade> trades) {
        List<BacktestPaperTrade> safeTrades = trades == null ? List.of() : trades;
        BacktestClvStatus effectiveStatus = status == null ? BacktestClvStatus.NOT_AVAILABLE : status;
        if (effectiveStatus != BacktestClvStatus.VALID_PROSPECTIVE) {
            return new BacktestClvSummary(effectiveStatus, safeTrades.size(), null, null, null);
        }
        List<BigDecimal> clvs = safeTrades.stream()
            .map(BacktestPaperTrade::decimalClvRatio)
            .filter(java.util.Objects::nonNull)
            .sorted()
            .toList();
        if (clvs.isEmpty()) {
            return new BacktestClvSummary(
                effectiveStatus,
                0,
                BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
        }
        BigDecimal total = clvs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(clvs.size()), 8, RoundingMode.HALF_UP);
        BigDecimal median = clvs.get(clvs.size() / 2);
        if (clvs.size() % 2 == 0) {
            median = clvs.get(clvs.size() / 2 - 1).add(clvs.get(clvs.size() / 2))
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        long positive = clvs.stream().filter(value -> value.compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal positivePercent = BigDecimal.valueOf(positive)
            .divide(BigDecimal.valueOf(clvs.size()), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
        return new BacktestClvSummary(effectiveStatus, clvs.size(), average, median, positivePercent);
    }
}
