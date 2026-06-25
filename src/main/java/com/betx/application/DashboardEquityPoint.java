package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Cumulative realized PnL and drawdown point for dashboard charts. */
public record DashboardEquityPoint(
    Instant timestamp,
    BigDecimal cumulativePnl,
    BigDecimal equity,
    BigDecimal drawdown,
    BigDecimal pnl,
    BigDecimal dailyPnl,
    long trades,
    long sequenceNumber,
    BigDecimal cumulativeRoi
) {
}
