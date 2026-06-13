package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcBetIntentRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsResultMessageExternalOrderIdAndSupportsRiskQueries() {
        JdbcBetIntentRepository repository = new JdbcBetIntentRepository(tempDir.resolve("betx.db").toString());
        String databasePath = tempDir.resolve("betx.db").toString();
        BetIntent first = intent(
            "first",
            BetIntentSource.AUTOMATIC,
            "1.1",
            42L,
            BetIntentStage.EXECUTED,
            "accepted",
            "bet-123",
            "2026-06-05T09:00:00Z"
        );
        BetIntent second = intent(
            "second",
            BetIntentSource.TELEGRAM_CONFIRMATION,
            "1.2",
            43L,
            BetIntentStage.AWAITING_CONFIRMATION,
            null,
            "2026-06-05T09:05:00Z"
        );

        repository.save(databasePath, first);
        repository.save(databasePath, second);

        assertThat(repository.findById(databasePath, "first"))
            .hasValueSatisfying(intent -> {
                assertThat(intent.resultMessage()).isEqualTo("accepted");
                assertThat(intent.externalOrderId()).isEqualTo("bet-123");
                assertThat(intent.source()).isEqualTo(BetIntentSource.AUTOMATIC);
            });
        assertThat(repository.listRecent(databasePath, 10))
            .extracting(BetIntent::id)
            .containsExactly("second", "first");
        assertThat(repository.listByStages(databasePath, List.of(BetIntentStage.AWAITING_CONFIRMATION), 10))
            .extracting(BetIntent::id)
            .containsExactly("second");
        assertThat(repository.countByStages(databasePath, List.of(
            BetIntentStage.AWAITING_CONFIRMATION,
            BetIntentStage.EXECUTED
        ))).isEqualTo(2L);
        assertThat(repository.sumSelectedStakeByStageSince(
            databasePath,
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-05T00:00:00Z")
        )).isEqualByComparingTo("5.00");
        assertThat(repository.findLatestByKeySince(
            databasePath,
            "betfair",
            "1.1",
            42L,
            Instant.parse("2026-06-05T08:00:00Z")
        )).hasValueSatisfying(intent -> assertThat(intent.id()).isEqualTo("first"));
    }

    @Test
    void createsGenericBetIntentsTableWithoutLegacyTelegramIntentMigrationCode() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE bet_intents (
                    id TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    event_name TEXT,
                    market_name TEXT,
                    runner_name TEXT,
                    reason TEXT,
                    odds TEXT NOT NULL,
                    max_stake TEXT NOT NULL,
                    available_balance TEXT,
                    selected_stake TEXT,
                    stage TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
        }
        JdbcBetIntentRepository repository = new JdbcBetIntentRepository(database.toString());

        repository.save(database.toString(), intent(
            "first",
            BetIntentSource.AUTOMATIC,
            "1.1",
            42L,
            BetIntentStage.FAILED,
            "blocked",
            "bet-123",
            "2026-06-05T09:00:00Z"
        ));

        assertThat(repository.findById(database.toString(), "first"))
            .hasValueSatisfying(intent -> {
                assertThat(intent.resultMessage()).isEqualTo("blocked");
                assertThat(intent.externalOrderId()).isEqualTo("bet-123");
                assertThat(intent.source()).isEqualTo(BetIntentSource.AUTOMATIC);
            });
    }

    private BetIntent intent(
        String id,
        BetIntentSource source,
        String marketId,
        long selectionId,
        BetIntentStage stage,
        String resultMessage,
        String updatedAt
    ) {
        return new BetIntent(
            id,
            source,
            "betfair",
            marketId,
            selectionId,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(5),
            resultMessage,
            null,
            stage,
            Instant.parse("2026-06-05T08:00:00Z"),
            Instant.parse(updatedAt)
        );
    }

    private BetIntent intent(
        String id,
        BetIntentSource source,
        String marketId,
        long selectionId,
        BetIntentStage stage,
        String resultMessage,
        String externalOrderId,
        String updatedAt
    ) {
        return new BetIntent(
            id,
            source,
            "betfair",
            marketId,
            selectionId,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(5),
            resultMessage,
            externalOrderId,
            stage,
            Instant.parse("2026-06-05T08:00:00Z"),
            Instant.parse(updatedAt)
        );
    }
}
