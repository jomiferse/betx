package com.betx.application;

import java.time.Instant;

/** Unique key for one persisted signal history decision. */
public record SignalHistoryKey(
    String exchange,
    String marketId,
    long selectionId,
    Instant observedAt
) {
    public SignalHistoryKey {
        if (exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank() || selectionId <= 0 || observedAt == null) {
            throw new IllegalArgumentException("exchange, marketId, selectionId, and observedAt are required.");
        }
    }
}
