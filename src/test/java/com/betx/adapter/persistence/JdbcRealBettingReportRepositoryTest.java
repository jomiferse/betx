package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.RealBettingReportRow;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcRealBettingReportRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void readsBetIntentRowsWithOptionalSignalHistoryCompetition() throws Exception {
        Path database = tempDir.resolve("betx.db");
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
                    competition_name TEXT,
                    selection_side TEXT NOT NULL DEFAULT 'UNKNOWN',
                    strategy_name TEXT,
                    side TEXT NOT NULL DEFAULT 'BACK',
                    reason TEXT,
                    odds TEXT NOT NULL,
                    max_stake TEXT NOT NULL,
                    available_balance TEXT,
                    effective_available_balance TEXT,
                    reserved_balance TEXT,
                    balance_snapshot_at TEXT,
                    selected_stake TEXT,
                    stage TEXT NOT NULL,
                    result_message TEXT,
                    external_order_id TEXT,
                    settled_at TEXT,
                    settlement_result TEXT,
                    realized_profit_loss TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE signal_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    competition_name TEXT,
                    recommendation TEXT NOT NULL,
                    score INTEGER NOT NULL DEFAULT 0,
                    bet_intent_id TEXT
                )
                """);
            statement.executeUpdate("""
                INSERT INTO bet_intents (
                    id, source, exchange, market_id, selection_id, event_name, market_name, runner_name,
                    competition_name, selection_side, strategy_name, odds, max_stake,
                    available_balance, effective_available_balance, selected_stake, stage, external_order_id, settled_at,
                    settlement_result, realized_profit_loss, created_at, updated_at
                ) VALUES (
                    'intent-1', 'AUTOMATIC', 'betfair', '1.1', 42, 'Team A v Team B', 'Match Odds', 'Team A',
                    'La Liga Persisted', 'HOME', 'value-football', '2.50', '5.00',
                    '110.00', '105.00', '5.00', 'SETTLED', 'bet-1', '2026-06-01T12:00:00Z',
                    'WIN', '7.50', '2026-06-01T10:00:00Z', '2026-06-01T12:00:00Z'
                )
                """);
            statement.executeUpdate("""
                INSERT INTO bet_intents (
                    id, source, exchange, market_id, selection_id, event_name, market_name, runner_name, odds, max_stake,
                    selected_stake, stage, created_at, updated_at
                ) VALUES (
                    'intent-2', 'AUTOMATIC', 'betfair', '1.2', 43, 'Team C v Team D', 'Match Odds', 'DRAW', '3.20', '5.00',
                    '4.00', 'EXECUTED', '2026-06-02T10:00:00Z', '2026-06-02T10:01:00Z'
                )
                """);
            statement.executeUpdate("""
                INSERT INTO signal_history (
                    observed_at, exchange, market_id, selection_id, competition_name, recommendation, score, bet_intent_id
                ) VALUES (
                    '2026-06-01T09:58:00Z', 'betfair', '1.1', 42, 'La Liga', 'BET', 80, 'intent-1'
                )
                """);
        }

        List<RealBettingReportRow> rows = new JdbcRealBettingReportRepository().listReportRows(database.toString());

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).satisfies(row -> {
            assertThat(row.id()).isEqualTo("intent-1");
            assertThat(row.stage()).isEqualTo(BetIntentStage.SETTLED);
            assertThat(row.settlementResult()).isEqualTo(BetSettlementResult.WIN);
            assertThat(row.realizedProfitLoss()).isEqualByComparingTo("7.50");
            assertThat(row.competitionName()).isEqualTo("La Liga Persisted");
            assertThat(row.selectionSide()).isEqualTo(com.betx.domain.order.SelectionSide.HOME);
            assertThat(row.strategyName()).isEqualTo("value-football");
            assertThat(row.effectiveAvailableBalance()).isEqualByComparingTo("105.00");
        });
        assertThat(rows.get(1)).satisfies(row -> {
            assertThat(row.id()).isEqualTo("intent-2");
            assertThat(row.stage()).isEqualTo(BetIntentStage.EXECUTED);
            assertThat(row.competitionName()).isEqualTo("N/A");
            assertThat(row.selectionSide()).isEqualTo(com.betx.domain.order.SelectionSide.UNKNOWN);
            assertThat(row.strategyName()).isEqualTo("N/A");
        });
    }
}
