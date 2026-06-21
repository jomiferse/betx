package com.betx.application;

/** Aggregated operational skips for already-covered real betting opportunities. */
public record DiagnosticsSkippedMarket(
    String eventName,
    String runnerName,
    String marketId,
    long selectionId,
    String side,
    String existingBetIntentId,
    String existingExecutionStatus,
    long attempts
) {
}
