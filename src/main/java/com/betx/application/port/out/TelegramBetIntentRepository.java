package com.betx.application.port.out;

import com.betx.domain.telegram.TelegramBetIntent;
import java.util.Optional;

/** Persists live bet confirmation state and Telegram polling offsets. */
public interface TelegramBetIntentRepository {
    Optional<TelegramBetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId);

    Optional<TelegramBetIntent> findById(String databasePath, String id);

    void save(String databasePath, TelegramBetIntent intent);

    void update(String databasePath, TelegramBetIntent intent);

    long loadLastProcessedUpdateId(String databasePath);

    void saveLastProcessedUpdateId(String databasePath, long updateId);
}
