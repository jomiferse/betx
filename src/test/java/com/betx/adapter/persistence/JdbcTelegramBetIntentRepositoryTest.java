package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTelegramBetIntentRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsResultMessageAndSupportsRiskQueries() {
        JdbcTelegramBetIntentRepository repository = new JdbcTelegramBetIntentRepository(tempDir.resolve("betx.db").toString());
        String databasePath = tempDir.resolve("betx.db").toString();
        TelegramBetIntent first = intent("first", "1.1", 42L, TelegramBetIntentStage.EXECUTED, "accepted", "2026-06-05T09:00:00Z");
        TelegramBetIntent second = intent("second", "1.2", 43L, TelegramBetIntentStage.AWAITING_CONFIRMATION, null, "2026-06-05T09:05:00Z");

        repository.save(databasePath, first);
        repository.save(databasePath, second);

        assertThat(repository.findById(databasePath, "first"))
            .hasValueSatisfying(intent -> assertThat(intent.resultMessage()).isEqualTo("accepted"));
        assertThat(repository.listRecent(databasePath, 10))
            .extracting(TelegramBetIntent::id)
            .containsExactly("second", "first");
        assertThat(repository.countByStages(databasePath, List.of(
            TelegramBetIntentStage.AWAITING_CONFIRMATION,
            TelegramBetIntentStage.EXECUTED
        ))).isEqualTo(2L);
        assertThat(repository.sumSelectedStakeByStageSince(
            databasePath,
            TelegramBetIntentStage.EXECUTED,
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
    void addsResultMessageColumnToExistingDatabase() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE telegram_bet_intents (
                    id TEXT PRIMARY KEY,
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
        JdbcTelegramBetIntentRepository repository = new JdbcTelegramBetIntentRepository(database.toString());

        repository.save(database.toString(), intent("first", "1.1", 42L, TelegramBetIntentStage.FAILED, "blocked", "2026-06-05T09:00:00Z"));

        assertThat(repository.findById(database.toString(), "first"))
            .hasValueSatisfying(intent -> assertThat(intent.resultMessage()).isEqualTo("blocked"));
    }

    private TelegramBetIntent intent(
        String id,
        String marketId,
        long selectionId,
        TelegramBetIntentStage stage,
        String resultMessage,
        String updatedAt
    ) {
        return new TelegramBetIntent(
            id,
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
            stage,
            Instant.parse("2026-06-05T08:00:00Z"),
            Instant.parse(updatedAt)
        );
    }
}
