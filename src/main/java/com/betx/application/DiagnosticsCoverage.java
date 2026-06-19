package com.betx.application;

public record DiagnosticsCoverage(
    int realBets,
    int paperTrades,
    int matchedPairs,
    int realOnly,
    int paperOnly,
    int ambiguous
) {
}
