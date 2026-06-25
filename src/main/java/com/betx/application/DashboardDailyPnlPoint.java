package com.betx.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily realized PnL aggregate for dashboard charts. */
public record DashboardDailyPnlPoint(
    LocalDate day,
    long trades,
    long wonTrades,
    long lostTrades,
    BigDecimal totalStake,
    BigDecimal pnl,
    BigDecimal roi
) {
}
