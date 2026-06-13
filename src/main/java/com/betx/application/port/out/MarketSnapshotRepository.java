package com.betx.application.port.out;

import com.betx.domain.signal.ObservedMarketSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists normalized market snapshots for change comparison. */
public interface MarketSnapshotRepository {
    Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId);

    default List<ObservedMarketSnapshot> findRecent(String databasePath, String exchange, String marketId, long selectionId, int limit) {
        return findLatest(databasePath, exchange, marketId, selectionId).stream().limit(limit).toList();
    }

    void save(String databasePath, ObservedMarketSnapshot snapshot);

    default int deleteExpiredMarkets(String databasePath, Instant marketStartTimeBefore) {
        return 0;
    }

    default int deleteMarket(String databasePath, String exchange, String marketId) {
        return 0;
    }
}
