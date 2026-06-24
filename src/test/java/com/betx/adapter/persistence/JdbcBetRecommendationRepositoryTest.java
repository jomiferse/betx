package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetRecommendation;
import com.betx.application.BetRecommendationSource;
import com.betx.application.BetRecommendationStatus;
import com.betx.application.BetRecommendationUpsertAction;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
                    "idx_bet_recommendations_canonical_key",
                    "idx_bet_recommendations_canonical_status",
                    "idx_bet_recommendations_last_seen_at",
                    "idx_bet_recommendations_match_key",
                    "idx_bet_recommendations_recommended_at",
                    "idx_bet_recommendations_status",
                    "idx_bet_recommendations_strategy_name"
                );
        }
    }

    @Test
    void upsertInsertsFirstCanonicalObservation() {
        String databasePath = tempDir.resolve("upsert-insert.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        BetRecommendation recommendation = recommendation("rec-1", "eval-1");

        var result = repository.upsertActiveRecommendation(databasePath, recommendation);

        assertThat(result.action()).isEqualTo(BetRecommendationUpsertAction.CREATED);
        assertThat(result.recommendation().id()).isEqualTo("rec-1");
        assertThat(result.recommendation().status()).isEqualTo(BetRecommendationStatus.ACTIVE);
        assertThat(result.recommendation().canonicalKey()).isEqualTo("betfair|1.234|42|HOME|value-football");
        assertThat(result.recommendation().observedCount()).isEqualTo(1);
        assertThat(repository.findById(databasePath, "rec-1")).contains(result.recommendation());
    }

    @Test
    void upsertUpdatesRepeatedCanonicalObservationWithoutInsertingDuplicate() throws Exception {
        String databasePath = tempDir.resolve("upsert-update.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        BetRecommendation first = recommendation("rec-1", "eval-1");
        BetRecommendation second = recommendation("rec-2", "eval-2", new BigDecimal("2.70"), Instant.parse("2026-06-22T08:30:00Z"));

        repository.upsertActiveRecommendation(databasePath, first);
        var result = repository.upsertActiveRecommendation(databasePath, second);

        assertThat(result.action()).isEqualTo(BetRecommendationUpsertAction.OBSERVED);
        assertThat(result.recommendation().id()).isEqualTo("rec-1");
        assertThat(result.recommendation().evaluationId()).isEqualTo("eval-1");
        assertThat(result.recommendation().lastEvaluationId()).isEqualTo("eval-2");
        assertThat(result.recommendation().firstSeenAt()).isEqualTo(Instant.parse("2026-06-22T08:25:14Z"));
        assertThat(result.recommendation().lastSeenAt()).isEqualTo(Instant.parse("2026-06-22T08:30:00Z"));
        assertThat(result.recommendation().observedCount()).isEqualTo(2);
        assertThat(result.recommendation().initialRecommendedOdds()).isEqualByComparingTo("2.50");
        assertThat(result.recommendation().latestRecommendedOdds()).isEqualByComparingTo("2.70");
        assertThat(result.recommendation().bestRecommendedOdds()).isEqualByComparingTo("2.70");
        assertThat(countRows(databasePath)).isEqualTo(1);
    }

    @Test
    void upsertKeepsCoveredCanonicalRecommendationCovered() throws Exception {
        String databasePath = tempDir.resolve("upsert-covered.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        repository.upsertActiveRecommendation(databasePath, recommendation("rec-1", "eval-1"));

        var covered = repository.markCovered(databasePath, "betfair|1.234|42|HOME|value-football", Instant.parse("2026-06-22T08:40:00Z"));
        var observed = repository.upsertActiveRecommendation(
            databasePath,
            recommendation("rec-2", "eval-2", new BigDecimal("2.80"), Instant.parse("2026-06-22T08:45:00Z"))
        );

        assertThat(covered).isPresent();
        assertThat(observed.action()).isEqualTo(BetRecommendationUpsertAction.ALREADY_COVERED);
        assertThat(observed.recommendation().status()).isEqualTo(BetRecommendationStatus.COVERED);
        assertThat(observed.recommendation().coveredAt()).isEqualTo(Instant.parse("2026-06-22T08:40:00Z"));
        assertThat(observed.recommendation().observedCount()).isEqualTo(2);
        assertThat(countRows(databasePath)).isEqualTo(1);
    }

    @Test
    void markCoveredReportsTransitionOnlyOnceAndKeepsOriginalCoveredAt() throws Exception {
        String databasePath = tempDir.resolve("mark-covered-once.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        repository.upsertActiveRecommendation(databasePath, recommendation("rec-1", "eval-1"));

        var first = repository.markCovered(
            databasePath,
            "betfair|1.234|42|HOME|value-football",
            Instant.parse("2026-06-22T08:40:00Z")
        );
        var second = repository.markCovered(
            databasePath,
            "betfair|1.234|42|HOME|value-football",
            Instant.parse("2026-06-22T08:45:00Z")
        );

        assertThat(first).isPresent();
        assertThat(first.get().action()).isEqualTo(BetRecommendationUpsertAction.COVERED);
        assertThat(first.get().recommendation().status()).isEqualTo(BetRecommendationStatus.COVERED);
        assertThat(first.get().recommendation().coveredAt()).isEqualTo(Instant.parse("2026-06-22T08:40:00Z"));
        assertThat(second).isPresent();
        assertThat(second.get().action()).isEqualTo(BetRecommendationUpsertAction.ALREADY_COVERED);
        assertThat(second.get().recommendation().status()).isEqualTo(BetRecommendationStatus.COVERED);
        assertThat(second.get().recommendation().coveredAt()).isEqualTo(Instant.parse("2026-06-22T08:40:00Z"));
        assertThat(countRows(databasePath)).isEqualTo(1);
    }

    @Test
    void concurrentUpsertsForSameCanonicalKeyCreateSingleRow() throws Exception {
        String databasePath = tempDir.resolve("upsert-concurrent.db").toString();
        JdbcBetRecommendationRepository repository = new JdbcBetRecommendationRepository(databasePath);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return repository.upsertActiveRecommendation(databasePath, recommendation("rec-1", "eval-1"));
            });
            var second = executor.submit(() -> {
                start.await();
                return repository.upsertActiveRecommendation(
                    databasePath,
                    recommendation("rec-2", "eval-2", new BigDecimal("2.60"), Instant.parse("2026-06-22T08:26:00Z"))
                );
            });
            start.countDown();
            List.of(first.get(), second.get());
        }

        assertThat(countRows(databasePath)).isEqualTo(1);
        assertThat(repository.findById(databasePath, "rec-1")
            .or(() -> repository.findById(databasePath, "rec-2")))
            .hasValueSatisfying(recommendation -> assertThat(recommendation.observedCount()).isEqualTo(2));
    }

    private static long countRows(String databasePath) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             var resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) AS total FROM bet_recommendations")) {
            return resultSet.next() ? resultSet.getLong("total") : 0;
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
        return recommendation(id, evaluationId, new BigDecimal("2.50"), Instant.parse("2026-06-22T08:25:14Z"));
    }

    private static BetRecommendation recommendation(String id, String evaluationId, BigDecimal odds, Instant observedAt) {
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
            odds,
            observedAt,
            observedAt.plusSeconds(1),
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            observedAt.plusSeconds(1),
            null,
            null,
            null,
            null
        );
    }
}
