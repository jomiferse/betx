package com.betx.application.port.out;

import com.betx.application.PaperTrade;
import java.util.List;
import java.util.Optional;

/** Stores prospective paper trades independently from real bet intents. */
public interface PaperTradeRepository {
    Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId);

    void upsert(String databasePath, PaperTrade trade);

    List<PaperTrade> listAll(String databasePath);
}
