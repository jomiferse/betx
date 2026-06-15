package com.betx.adapter.backtest;

/** Summary for a Football-Data to BetX backtest CSV conversion. */
public record FootballDataConversionResult(
    int matchesRead,
    int rowsWritten,
    int duplicatesSkipped
) {
    public FootballDataConversionResult(int matchesRead, int rowsWritten) {
        this(matchesRead, rowsWritten, 0);
    }
}
