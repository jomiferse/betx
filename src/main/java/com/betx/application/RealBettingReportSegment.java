package com.betx.application;

import java.math.BigDecimal;

/** Aggregated settled real-betting metrics for one report segment. */
public record RealBettingReportSegment(
    String name,
    long settledBets,
    long wins,
    long losses,
    long voids,
    BigDecimal totalStaked,
    BigDecimal netRealizedPnl,
    BigDecimal roiPercent,
    BigDecimal winRatePercent
) {
    public RealBettingReportSegment {
        name = name == null || name.isBlank() ? "N/A" : name.strip();
        totalStaked = totalStaked == null ? BigDecimal.ZERO : totalStaked;
        netRealizedPnl = netRealizedPnl == null ? BigDecimal.ZERO : netRealizedPnl;
        roiPercent = roiPercent == null ? BigDecimal.ZERO : roiPercent;
        winRatePercent = winRatePercent == null ? BigDecimal.ZERO : winRatePercent;
    }
}
