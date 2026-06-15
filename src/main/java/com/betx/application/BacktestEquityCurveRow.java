package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

/** One row in the focused value-football-draw-only cumulative equity curve. */
public record BacktestEquityCurveRow(
    Instant observedAt,
    String league,
    String season,
    String event,
    BigDecimal odds,
    BacktestOutcome result,
    BigDecimal pnl,
    BigDecimal cumulativePnl,
    BigDecimal drawdown
) {
    public BacktestEquityCurveRow {
        league = league == null || league.isBlank() ? "unknown" : league;
        season = season == null || season.isBlank() ? "unknown" : season;
        event = event == null || event.isBlank() ? "unknown" : event;
        odds = odds == null ? BigDecimal.ZERO : odds;
        pnl = pnl == null ? BigDecimal.ZERO : pnl;
        cumulativePnl = cumulativePnl == null ? BigDecimal.ZERO : cumulativePnl;
        drawdown = drawdown == null ? BigDecimal.ZERO : drawdown;
    }
}
