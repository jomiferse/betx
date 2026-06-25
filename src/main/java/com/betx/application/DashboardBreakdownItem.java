package com.betx.application;

import java.math.BigDecimal;

/** Segment-level performance item prepared for dashboard breakdowns. */
public record DashboardBreakdownItem(
    String name,
    long trades,
    long wonTrades,
    long lostTrades,
    BigDecimal pnl,
    BigDecimal roi,
    BigDecimal winRate
) {
}
