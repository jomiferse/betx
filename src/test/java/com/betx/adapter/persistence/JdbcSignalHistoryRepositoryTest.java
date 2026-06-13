package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.MatchIntelligenceDecision;
import com.betx.application.SignalHistoryEntry;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcSignalHistoryRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsSchemaAndUpsertsDecisionBySignalKey() {
        JdbcSignalHistoryRepository repository = new JdbcSignalHistoryRepository(tempDir.resolve("betx.db").toString());
        String databasePath = tempDir.resolve("betx.db").toString();
        SignalHistoryEntry first = entry(RecommendationType.BET, 80, "first reason");
        SignalHistoryEntry second = entry(RecommendationType.BET, 88, "updated reason");

        repository.saveDecision(databasePath, first);
        repository.saveDecision(databasePath, second);

        assertThat(repository.count(databasePath)).isEqualTo(1L);
        assertThat(repository.findLatest(databasePath, "betfair", "1.1", 42L))
            .hasValueSatisfying(saved -> {
                assertThat(saved.score()).isEqualTo(88);
                assertThat(saved.reason()).isEqualTo("updated reason");
                assertThat(saved.recommendation()).isEqualTo(RecommendationType.BET);
                assertThat(saved.bestBackPrice()).isEqualByComparingTo("2.50");
                assertThat(saved.backPercentageDelta()).isEqualByComparingTo("-3.85");
                assertThat(saved.liquidityPercentageDelta()).isEqualByComparingTo("20.00");
                assertThat(saved.intelligenceDecision()).isEqualTo(MatchIntelligenceDecision.APPROVE);
                assertThat(saved.intelligenceConfidence()).isEqualTo(84);
                assertThat(saved.intelligenceSummary()).isEqualTo("No negative team news found.");
            });
    }

    @Test
    void linksIntentAndUpdatesOrderLifecycleFields() {
        JdbcSignalHistoryRepository repository = new JdbcSignalHistoryRepository(tempDir.resolve("betx.db").toString());
        String databasePath = tempDir.resolve("betx.db").toString();
        SignalHistoryEntry decision = entry(RecommendationType.WATCH, 62, "watch reason");
        repository.saveDecision(databasePath, decision);

        BetIntent awaiting = intent("intent-1", BetIntentStage.AWAITING_CONFIRMATION, null, null, "Stake pending.");
        repository.linkIntent(databasePath, decision.key(), awaiting);
        BetIntent executed = intent("intent-1", BetIntentStage.EXECUTED, BigDecimal.valueOf(5), "bet-123", "accepted");
        repository.updateOrderState(databasePath, executed);

        assertThat(repository.findLatest(databasePath, "betfair", "1.1", 42L))
            .hasValueSatisfying(saved -> {
                assertThat(saved.betIntentId()).isEqualTo("intent-1");
                assertThat(saved.orderStage()).isEqualTo("EXECUTED");
                assertThat(saved.selectedStake()).isEqualByComparingTo("5");
                assertThat(saved.externalOrderId()).isEqualTo("bet-123");
                assertThat(saved.resultMessage()).isEqualTo("accepted");
                assertThat(saved.realizedProfitLoss()).isNull();
            });
    }

    @Test
    void migratesMissingColumnsOnExistingDatabase() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE signal_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    recommendation TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE UNIQUE INDEX idx_signal_history_unique_decision
                ON signal_history(exchange, market_id, selection_id, observed_at)
                """);
        }
        JdbcSignalHistoryRepository repository = new JdbcSignalHistoryRepository(database.toString());

        repository.saveDecision(database.toString(), entry(RecommendationType.BET, 80, "migrated"));
        repository.linkIntent(database.toString(), entry(RecommendationType.BET, 80, "migrated").key(), intent(
            "intent-1",
            BetIntentStage.FAILED,
            BigDecimal.valueOf(3),
            null,
            "blocked"
        ));

        assertThat(repository.findLatest(database.toString(), "betfair", "1.1", 42L))
            .hasValueSatisfying(saved -> {
                assertThat(saved.eventName()).isEqualTo("Team A v Team B");
                assertThat(saved.betIntentId()).isEqualTo("intent-1");
                assertThat(saved.orderStage()).isEqualTo("FAILED");
                assertThat(saved.resultMessage()).isEqualTo("blocked");
            });
    }

    private SignalHistoryEntry entry(RecommendationType recommendation, int score, String reason) {
        return new SignalHistoryEntry(
            Instant.parse("2026-05-31T10:01:00Z"),
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            recommendation,
            score,
            "High confidence",
            reason,
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(2.60),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200),
            BigDecimal.valueOf(-3.85),
            BigDecimal.valueOf(-3.70),
            BigDecimal.valueOf(20.00),
            MatchIntelligenceDecision.APPROVE,
            84,
            "No negative team news found.",
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private BetIntent intent(
        String id,
        BetIntentStage stage,
        BigDecimal selectedStake,
        String externalOrderId,
        String resultMessage
    ) {
        return new BetIntent(
            id,
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(20),
            selectedStake,
            resultMessage,
            externalOrderId,
            stage,
            Instant.parse("2026-05-31T10:02:00Z"),
            Instant.parse("2026-05-31T10:03:00Z")
        );
    }
}
