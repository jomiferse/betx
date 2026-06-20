package com.betx.application.port.out;

import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists live bet execution intents. */
public interface BetIntentRepository {
    Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId);

    default Optional<BetIntent> findDuplicateBlockingByKey(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        BetSide side
    ) {
        return findActiveByKey(databasePath, exchange, marketId, selectionId);
    }

    default Optional<BetIntent> claimDuplicateProtectionKey(String databasePath, BetIntent intent) {
        return Optional.empty();
    }

    default void releaseDuplicateProtectionKey(String databasePath, BetIntent intent) {
    }

    default Optional<BetIntent> findActiveByMarket(String databasePath, String exchange, String marketId) {
        return Optional.empty();
    }

    Optional<BetIntent> findLatestByKeySince(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        Instant since
    );

    default Optional<BetIntent> findLatestByMarketSince(
        String databasePath,
        String exchange,
        String marketId,
        Instant since
    ) {
        return Optional.empty();
    }

    default Optional<BetIntent> findLatestByExchangeResultSince(
        String databasePath,
        String exchange,
        String resultMessage,
        Instant since
    ) {
        return Optional.empty();
    }

    Optional<BetIntent> findById(String databasePath, String id);

    List<BetIntent> listRecent(String databasePath, int limit);

    List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit);

    long countByStages(String databasePath, List<BetIntentStage> stages);

    BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since);

    void save(String databasePath, BetIntent intent);

    void update(String databasePath, BetIntent intent);
}
