package com.betx.application.port.out;

import com.betx.domain.signal.ObservedMarketSnapshot;
import java.util.Optional;

/** Persists normalized market snapshots for change comparison. */
public interface MarketSnapshotRepository {
    Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId);

    void save(String databasePath, ObservedMarketSnapshot snapshot);
}
