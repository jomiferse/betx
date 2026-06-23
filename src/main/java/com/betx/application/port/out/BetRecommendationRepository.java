package com.betx.application.port.out;

import com.betx.application.BetRecommendation;
import com.betx.application.BetRecommendationUpsertResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BetRecommendationRepository {
    void save(String databasePath, BetRecommendation recommendation);

    default BetRecommendationUpsertResult upsertActiveRecommendation(String databasePath, BetRecommendation recommendation) {
        save(databasePath, recommendation);
        return new BetRecommendationUpsertResult(
            recommendation,
            com.betx.application.BetRecommendationUpsertAction.CREATED
        );
    }

    default Optional<BetRecommendationUpsertResult> markCovered(String databasePath, String canonicalKey, Instant coveredAt) {
        return Optional.empty();
    }

    Optional<BetRecommendation> findById(String databasePath, String id);

    List<BetRecommendation> findByEvaluationId(String databasePath, String evaluationId);
}
