package com.betx.application;

import java.math.BigDecimal;

/** Performance metrics for the most recent settled real bets. */
public record RealBettingReportRollingWindow(
    int requestedSize,
    int availableSettledBets,
    long wins,
    long losses,
    BigDecimal winRatePercent,
    BigDecimal totalStaked,
    BigDecimal netRealizedPnl,
    BigDecimal roiPercent,
    BigDecimal maximumDrawdown,
    int maxWinningStreak,
    int maxLosingStreak
) {
    public RealBettingReportRollingWindow {
        totalStaked = totalStaked == null ? BigDecimal.ZERO : totalStaked;
        netRealizedPnl = netRealizedPnl == null ? BigDecimal.ZERO : netRealizedPnl;
        roiPercent = roiPercent == null ? BigDecimal.ZERO : roiPercent;
        winRatePercent = winRatePercent == null ? BigDecimal.ZERO : winRatePercent;
        maximumDrawdown = maximumDrawdown == null ? BigDecimal.ZERO : maximumDrawdown;
    }
}
