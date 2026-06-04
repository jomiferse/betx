package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcMarketSnapshotRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsSchemaAndReturnsLatestSnapshotForRunner() {
        JdbcMarketSnapshotRepository repository = new JdbcMarketSnapshotRepository(tempDir.resolve("betx.db").toString());
        ObservedMarketSnapshot first = observed("betfair", "1.234", 42L, "2026-05-31T10:00:00Z", BigDecimal.valueOf(2.50));
        ObservedMarketSnapshot second = observed("betfair", "1.234", 42L, "2026-05-31T10:01:00Z", BigDecimal.valueOf(2.44));

        repository.save(first);
        repository.save(second);

        assertThat(repository.findLatest("betfair", "1.234", 42L))
            .hasValueSatisfying(snapshot -> {
                assertThat(snapshot.observedAt()).isEqualTo(Instant.parse("2026-05-31T10:01:00Z"));
                assertThat(snapshot.snapshot().bestBackPrice()).isEqualByComparingTo("2.44");
                assertThat(snapshot.snapshot().runnerName()).isEqualTo("Team A");
            });
    }

    @Test
    void doesNotMixSelectionsMarketsOrExchanges() {
        JdbcMarketSnapshotRepository repository = new JdbcMarketSnapshotRepository(tempDir.resolve("betx.db").toString());
        repository.save(observed("betfair", "1.234", 42L, "2026-05-31T10:00:00Z", BigDecimal.valueOf(2.50)));
        repository.save(observed("betfair", "1.234", 43L, "2026-05-31T10:01:00Z", BigDecimal.valueOf(3.10)));
        repository.save(observed("matchbook", "1.234", 42L, "2026-05-31T10:02:00Z", BigDecimal.valueOf(2.20)));

        assertThat(repository.findLatest("betfair", "1.234", 42L))
            .hasValueSatisfying(snapshot -> assertThat(snapshot.snapshot().bestBackPrice()).isEqualByComparingTo("2.50"));
    }

    @Test
    void addsRunnerNameColumnToExistingDatabase() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE market_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    market_name TEXT,
                    event_name TEXT,
                    competition_name TEXT,
                    market_start_time TEXT,
                    selection_id INTEGER NOT NULL,
                    best_back_price TEXT,
                    best_lay_price TEXT,
                    spread TEXT,
                    liquidity TEXT NOT NULL
                )
                """);
        }
        JdbcMarketSnapshotRepository repository = new JdbcMarketSnapshotRepository(database.toString());

        repository.save(observed("betfair", "1.234", 42L, "2026-05-31T10:00:00Z", BigDecimal.valueOf(2.50)));

        assertThat(repository.findLatest("betfair", "1.234", 42L))
            .hasValueSatisfying(snapshot -> assertThat(snapshot.snapshot().runnerName()).isEqualTo("Team A"));
    }

    private ObservedMarketSnapshot observed(String exchange, String marketId, long selectionId, String observedAt, BigDecimal back) {
        return new ObservedMarketSnapshot(
            Instant.parse(observedAt),
            new MarketSnapshot(
                exchange,
                marketId,
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                selectionId,
                "Team A",
                back,
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_200)
            )
        );
    }
}
