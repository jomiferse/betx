package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

/** High-level performance metrics prepared for the read-only dashboard. */
public record DashboardSummaryView(
    BigDecimal totalPnl,
    BigDecimal roi,
    long totalTrades,
    long wonTrades,
    long lostTrades,
    BigDecimal winRate,
    BigDecimal totalStaked,
    BigDecimal maxDrawdown,
    BigDecimal openExposure,
    Instant lastUpdatedAt
) {
}
