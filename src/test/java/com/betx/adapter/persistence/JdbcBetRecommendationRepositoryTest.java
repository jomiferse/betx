package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetRecommendation;
import com.betx.application.BetRecommendationSource;
import com.betx.application.BetRecommendationStatus;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcBetRecommendationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void insertsReadsByIdAndFindsByEvaluationIdWithNullableOptionalFields() {
        String databasePath = tempDir.resolve("betx.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        BetRecommendation recommendation = recommendation("rec-1", "eval-1");

        repository.save(databasePath, recommendation);

        assertThat(repository.findById(databasePath, "rec-1")).contains(recommendation);
        assertThat(repository.findByEvaluationId(databasePath, "eval-1")).containsExactly(recommendation);
        assertThat(repository.findByEvaluationId(databasePath, "missing")).isEmpty();
    }

    @Test
    void initializesSchemaIdempotentlyAndCreatesIndexes() throws Exception {
        String databasePath = tempDir.resolve("schema.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);

        repository.save(databasePath, recommendation("rec-1", "eval-1"));
        repository.save(databasePath, recommendation("rec-2", "eval-2"));

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             var indexes = connection.createStatement().executeQuery("""
                 SELECT name
                 FROM sqlite_master
                 WHERE type = 'index' AND tbl_name = 'bet_recommendations'
                 ORDER BY name
                 """)) {
            assertThat(indexNames(indexes))
                .contains(
                    "idx_bet_recommendations_evaluation_id",
                    "idx_bet_recommendations_match_key",
                    "idx_bet_recommendations_recommended_at",
                    "idx_bet_recommendations_strategy_name"
                );
        }
    }

    private static java.util.List<String> indexNames(java.sql.ResultSet resultSet) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        while (resultSet.next()) {
            names.add(resultSet.getString("name"));
        }
        return names;
    }

    private static BetRecommendation recommendation(String id, String evaluationId) {
        return new BetRecommendation(
            id,
            evaluationId,
            "betfair",
            "1.234",
            42L,
            SelectionSide.HOME,
            "Team A v Team B",
            "Team A",
            "Premier League",
            Instant.parse("2026-06-22T20:00:00Z"),
            "value-football",
            new BigDecimal("2.50"),
            Instant.parse("2026-06-22T08:25:14Z"),
            Instant.parse("2026-06-22T08:25:15Z"),
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.CREATED,
            Instant.parse("2026-06-22T08:25:15Z"),
            null,
            null,
            null,
            null
        );
    }
}
