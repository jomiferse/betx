package com.betx.application;

/** Counts data-quality safeguards applied before strategy evaluation. */
public record BacktestLeakageDiagnostics(
    int rowsIgnoredAtOrAfterMarketStart,
    int duplicateRunnerRowsIgnored
) {
}
