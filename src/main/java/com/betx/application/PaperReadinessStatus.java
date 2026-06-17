package com.betx.application;

/** Readiness state for allowing fully automatic real betting. */
public enum PaperReadinessStatus {
    DISABLED,
    READY,
    NOT_READY,
    INSUFFICIENT_DATA,
    BLOCKED
}
