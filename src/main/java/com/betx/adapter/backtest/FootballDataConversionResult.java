package com.betx.adapter.backtest;

/** Summary for a Football-Data to BetX backtest CSV conversion. */
public record FootballDataConversionResult(
    int matchesRead,
    int rowsWritten
) {
}
