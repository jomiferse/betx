package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;

/** Execution-related counts read from persisted SQLite bet intent fields. */
public record DiagnosticsPersistedExecutionCoverage(
    long realBets,
    long betsWithOrderSubmittedAt,
    long betsWithExecutedAt,
    long settledRealBets,
    long fullyMatched,
    long partiallyMatched,
    long unmatched,
    long cancelled,
    DiagnosticsDataProvenance provenance
) {
}
