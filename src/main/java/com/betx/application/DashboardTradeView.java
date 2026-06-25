package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

/** One read-only trade row for the analytics dashboard. */
public record DashboardTradeView(
    Instant timestamp,
    String marketName,
    String selection,
    String strategy,
    BigDecimal odds,
    BigDecimal stake,
    String status,
    String result,
    BigDecimal pnl
) {
}
