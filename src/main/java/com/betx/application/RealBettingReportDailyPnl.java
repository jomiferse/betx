package com.betx.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily settled real-betting PnL summary. */
public record RealBettingReportDailyPnl(LocalDate day, long settledBets, BigDecimal pnl) {
    public RealBettingReportDailyPnl {
        pnl = pnl == null ? BigDecimal.ZERO : pnl;
    }
}
