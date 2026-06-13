package com.betx.application.port.out;

import com.betx.application.SignalHistoryEntry;
import com.betx.application.SignalHistoryKey;
import com.betx.domain.order.BetIntent;

/** Persists compact long-lived signal decisions and their order lifecycle. */
public interface SignalHistoryRepository {
    void saveDecision(String databasePath, SignalHistoryEntry entry);

    void linkIntent(String databasePath, SignalHistoryKey key, BetIntent intent);

    void updateOrderState(String databasePath, BetIntent intent);
}
