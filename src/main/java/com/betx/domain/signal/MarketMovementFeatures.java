package com.betx.domain.signal;

import java.math.BigDecimal;

/** Calculated local-history features used to score runner market movement. */
public record MarketMovementFeatures(
    BigDecimal oddsDeltaPercent,
    BigDecimal liquidityDeltaPercent,
    int favorablePersistenceCycles,
    BigDecimal volatilityPercent,
    int snapshotsUsed
) {
    public MarketMovementFeatures {
        favorablePersistenceCycles = Math.max(0, Math.min(3, favorablePersistenceCycles));
        snapshotsUsed = Math.max(0, snapshotsUsed);
    }
}
