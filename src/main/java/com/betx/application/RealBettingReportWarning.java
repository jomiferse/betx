package com.betx.application;

/** User-facing warning emitted with a real betting report. */
public record RealBettingReportWarning(String message) {
    public RealBettingReportWarning {
        message = message == null ? "" : message.strip();
    }
}
