package com.betx.application.port.out;

import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists live bet confirmation state and Telegram polling offsets. */
public interface TelegramBetIntentRepository {
    Optional<TelegramBetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId);

    Optional<TelegramBetIntent> findLatestByKeySince(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        Instant since
    );

    Optional<TelegramBetIntent> findById(String databasePath, String id);

    List<TelegramBetIntent> listRecent(String databasePath, int limit);

    List<TelegramBetIntent> listByStages(String databasePath, List<TelegramBetIntentStage> stages, int limit);

    long countByStages(String databasePath, List<TelegramBetIntentStage> stages);

    BigDecimal sumSelectedStakeByStageSince(String databasePath, TelegramBetIntentStage stage, Instant since);

    void save(String databasePath, TelegramBetIntent intent);

    void update(String databasePath, TelegramBetIntent intent);

    long loadLastProcessedUpdateId(String databasePath);

    void saveLastProcessedUpdateId(String databasePath, long updateId);
}
