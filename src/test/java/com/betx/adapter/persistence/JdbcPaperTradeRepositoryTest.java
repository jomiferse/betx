package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcPaperTradeRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertsPaperTradesByMarketSelectionAcrossRepositoryInstances() {
        String databasePath = tempDir.resolve("paper.db").toString();
        JdbcPaperTradeRepository writer = new JdbcPaperTradeRepository(databasePath);
        PaperTrade recommended = PaperTrade.recommended(snapshot(), Instant.parse("2026-06-15T10:00:00Z"), BigDecimal.valueOf(5));
        PaperTrade settled = recommended
            .withExecuted(Instant.parse("2026-06-15T10:01:00Z"), new BigDecimal("3.70"), true)
            .withClosed(Instant.parse("2026-06-15T17:50:00Z"), new BigDecimal("3.50"))
            .withSettled(Instant.parse("2026-06-15T20:00:00Z"), BacktestOutcome.WIN, new BigDecimal("0.05"));

        writer.upsert(databasePath, recommended);
        writer.upsert(databasePath, settled);

        JdbcPaperTradeRepository reader = new JdbcPaperTradeRepository(databasePath);
        assertThat(reader.listAll(databasePath)).singleElement().satisfies(trade -> {
            assertThat(trade.status()).isEqualTo(PaperTradeStatus.SETTLED);
            assertThat(trade.paperMode()).isTrue();
            assertThat(trade.exchange()).isEqualTo("betfair");
            assertThat(trade.marketId()).isEqualTo("1.234");
            assertThat(trade.selectionId()).isEqualTo(2L);
            assertThat(trade.side()).isEqualTo(BetSide.BACK);
            assertThat(trade.executionOdds()).isEqualByComparingTo("3.70");
            assertThat(trade.closingOdds()).isEqualByComparingTo("3.50");
            assertThat(trade.decimalClvRatio()).isEqualByComparingTo("0.05714286");
            assertThat(trade.grossPnl()).isEqualByComparingTo("13.50");
            assertThat(trade.commission()).isEqualByComparingTo("0.68");
            assertThat(trade.netPnl()).isEqualByComparingTo("12.82");
        });
        assertThat(reader.findByMarketSelection(databasePath, "betfair", "1.234", 2L))
            .get()
            .extracting(PaperTrade::status)
            .isEqualTo(PaperTradeStatus.SETTLED);
    }

    @Test
    void migratesLegacyPaperTradesWithoutSideAsBack() throws Exception {
        String databasePath = tempDir.resolve("legacy-paper.db").toString();
        createLegacyPaperTradeTable(databasePath);

        JdbcPaperTradeRepository reader = new JdbcPaperTradeRepository(databasePath);

        assertThat(reader.listAll(databasePath)).singleElement().satisfies(trade -> {
            assertThat(trade.side()).isEqualTo(BetSide.BACK);
            assertThat(trade.status()).isEqualTo(PaperTradeStatus.EXECUTED);
            assertThat(trade.runnerName()).isEqualTo("Draw");
        });
    }

    private static void createLegacyPaperTradeTable(String databasePath) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE paper_trades (
                    id TEXT PRIMARY KEY,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    event_name TEXT,
                    market_name TEXT,
                    league TEXT,
                    market_start_time TEXT,
                    runner_name TEXT,
                    status TEXT NOT NULL,
                    recommendation_timestamp TEXT NOT NULL,
                    available_back_odds TEXT,
                    requested_odds TEXT,
                    execution_timestamp TEXT,
                    execution_odds TEXT,
                    matched INTEGER NOT NULL,
                    closing_timestamp TEXT,
                    closing_odds TEXT,
                    settlement_timestamp TEXT,
                    result TEXT,
                    stake TEXT,
                    gross_pnl TEXT,
                    commission TEXT,
                    net_pnl TEXT,
                    decimal_clv_ratio TEXT,
                    implied_probability_change TEXT,
                    paper_mode INTEGER NOT NULL,
                    UNIQUE(exchange, market_id, selection_id)
                )
                """);
            statement.executeUpdate("""
                INSERT INTO paper_trades (
                    id, exchange, market_id, selection_id, event_name, market_name, league, market_start_time,
                    runner_name, status, recommendation_timestamp, available_back_odds, requested_odds,
                    execution_timestamp, execution_odds, matched, stake, gross_pnl, commission, net_pnl, paper_mode
                ) VALUES (
                    'betfair|1.234|2', 'betfair', '1.234', 2, 'Team A v Team B', 'Match Odds', 'SP1',
                    '2026-06-15T18:00:00Z', 'Draw', 'EXECUTED', '2026-06-15T10:00:00Z',
                    '3.70', '3.70', '2026-06-15T10:01:00Z', '3.70', 1, '5', '0', '0', '0', 1
                )
                """);
        }
    }

    private static MarketSnapshot snapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            2L,
            "Draw",
            new BigDecimal("3.70"),
            new BigDecimal("3.80"),
            new BigDecimal("0.03"),
            new BigDecimal("1200")
        );
    }
}
