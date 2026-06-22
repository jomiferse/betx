package com.betx.application.port.out;

import com.betx.application.BetRecommendation;
import java.util.List;
import java.util.Optional;

public interface BetRecommendationRepository {
    void save(String databasePath, BetRecommendation recommendation);

    Optional<BetRecommendation> findById(String databasePath, String id);

    List<BetRecommendation> findByEvaluationId(String databasePath, String evaluationId);
}
