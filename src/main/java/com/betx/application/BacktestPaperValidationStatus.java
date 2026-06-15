package com.betx.application;

/** Prospective evidence status for draw-only paper trading. */
public enum BacktestPaperValidationStatus {
    INSUFFICIENT_DATA,
    INSUFFICIENT_SAMPLE,
    NEGATIVE_EXECUTABLE_ROI,
    FRAGILE_EDGE,
    HISTORICAL_CANDIDATE,
    WEAK_EVIDENCE,
    EXECUTION_FAILURE,
    CANDIDATE_EDGE
}
