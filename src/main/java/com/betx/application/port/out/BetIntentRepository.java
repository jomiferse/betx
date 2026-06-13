package com.betx.application.port.out;

import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists live bet execution intents. */
public interface BetIntentRepository {
    Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId);

    Optional<BetIntent> findLatestByKeySince(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        Instant since
    );

    Optional<BetIntent> findById(String databasePath, String id);

    List<BetIntent> listRecent(String databasePath, int limit);

    List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit);

    long countByStages(String databasePath, List<BetIntentStage> stages);

    BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since);

    void save(String databasePath, BetIntent intent);

    void update(String databasePath, BetIntent intent);
}
