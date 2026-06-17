package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.signal.BetSide;
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
                assertThat(intent.side()).isEqualTo(BetSide.BACK);
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
        assertThat(repository.findActiveByKey(databasePath, "betfair", "1.1", 42L))
            .hasValueSatisfying(intent -> assertThat(intent.id()).isEqualTo("first"));
        assertThat(repository.findActiveByMarket(databasePath, "betfair", "1.1"))
            .hasValueSatisfying(intent -> assertThat(intent.id()).isEqualTo("first"));
        assertThat(repository.findLatestByMarketSince(
            databasePath,
            "betfair",
            "1.1",
            Instant.parse("2026-06-05T08:00:00Z")
        )).hasValueSatisfying(intent -> assertThat(intent.id()).isEqualTo("first"));
        assertThat(repository.findLatestByExchangeResultSince(
            databasePath,
            "betfair",
            "accepted",
            Instant.parse("2026-06-05T08:00:00Z")
        )).hasValueSatisfying(intent -> assertThat(intent.id()).isEqualTo("first"));
        assertThat(repository.findLatestByExchangeResultSince(
            databasePath,
            "betfair",
            "accepted",
            Instant.parse("2026-06-05T10:00:00Z")
        )).isEmpty();
    }

    @Test
    void persistsSettlementFieldsForExecutedOrders() {
        JdbcBetIntentRepository repository = new JdbcBetIntentRepository(tempDir.resolve("settlement.db").toString());
        String databasePath = tempDir.resolve("settlement.db").toString();
        BetIntent settled = intent(
            "settled",
            BetIntentSource.AUTOMATIC,
            "1.1",
            42L,
            BetIntentStage.EXECUTED,
            "accepted",
            "bet-123",
            "2026-06-05T09:00:00Z"
        ).withSettlement(
            BetIntentStage.SETTLED,
            BetSettlementResult.WIN,
            new BigDecimal("13.50"),
            Instant.parse("2026-06-05T18:30:00Z"),
            "Settled on exchange."
        );

        repository.save(databasePath, settled);

        assertThat(repository.findById(databasePath, "settled"))
            .hasValueSatisfying(intent -> {
                assertThat(intent.side()).isEqualTo(BetSide.BACK);
                assertThat(intent.stage()).isEqualTo(BetIntentStage.SETTLED);
                assertThat(intent.settlementResult()).isEqualTo(BetSettlementResult.WIN);
                assertThat(intent.realizedProfitLoss()).isEqualByComparingTo("13.50");
                assertThat(intent.settledAt()).isEqualTo(Instant.parse("2026-06-05T18:30:00Z"));
            });
    }

    @Test
    void persistsExecutionBalanceAuditFields() {
        JdbcBetIntentRepository repository = new JdbcBetIntentRepository(tempDir.resolve("execution-audit.db").toString());
        String databasePath = tempDir.resolve("execution-audit.db").toString();
        Instant snapshotAt = Instant.parse("2026-06-05T09:00:01Z");
        BetIntent intent = new BetIntent(
            "queued-1",
            BetIntentSource.AUTOMATIC,
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            new BigDecimal("17.70"),
            new BigDecimal("16.70"),
            BigDecimal.ONE,
            snapshotAt,
            BigDecimal.ONE,
            "accepted",
            "bet-123",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-05T09:00:00Z"),
            Instant.parse("2026-06-05T09:00:02Z")
        );

        repository.save(databasePath, intent);

        assertThat(repository.findById(databasePath, "queued-1"))
            .hasValueSatisfying(saved -> {
                assertThat(saved.availableBalance()).isEqualByComparingTo("17.70");
                assertThat(saved.effectiveAvailableBalance()).isEqualByComparingTo("16.70");
                assertThat(saved.reservedBalance()).isEqualByComparingTo("1");
                assertThat(saved.balanceSnapshotAt()).isEqualTo(snapshotAt);
            });
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
                assertThat(intent.side()).isEqualTo(BetSide.BACK);
                assertThat(intent.settlementResult()).isNull();
                assertThat(intent.realizedProfitLoss()).isNull();
                assertThat(intent.settledAt()).isNull();
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
