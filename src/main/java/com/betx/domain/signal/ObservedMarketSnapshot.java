package com.betx.domain.signal;

import java.time.Instant;

/** Market snapshot with the time it was observed by BetX. */
public record ObservedMarketSnapshot(
    Instant observedAt,
    MarketSnapshot snapshot
) {
    public ObservedMarketSnapshot {
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required.");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is required.");
        }
    }
}
