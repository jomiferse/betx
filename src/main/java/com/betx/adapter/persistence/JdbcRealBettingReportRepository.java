package com.betx.adapter.persistence;

import com.betx.application.RealBettingReportRow;
import com.betx.application.port.out.RealBettingReportRepository;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Read-only SQLite report repository for real betting evidence. */
@Component
public class JdbcRealBettingReportRepository implements RealBettingReportRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    @Override
    public List<RealBettingReportRow> listReportRows(String databasePath) {
        Path database = Path.of(databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath)
            .toAbsolutePath()
            .normalize();
        if (!Files.exists(database)) {
            return List.of();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            if (!tableExists(connection, "bet_intents")) {
                return List.of();
            }
            boolean canJoinSignalHistory = tableExists(connection, "signal_history")
                && columnExists(connection, "signal_history", "bet_intent_id")
                && columnExists(connection, "signal_history", "competition_name");
            boolean hasCompetition = columnExists(connection, "bet_intents", "competition_name");
            boolean hasSelectionSide = columnExists(connection, "bet_intents", "selection_side");
            boolean hasStrategy = columnExists(connection, "bet_intents", "strategy_name");
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query(canJoinSignalHistory, hasCompetition, hasSelectionSide, hasStrategy))) {
                List<RealBettingReportRow> rows = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    rows.add(map(resultSet));
                }
                return rows;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read real betting report rows.", exc);
        }
    }

    private static String query(
        boolean joinSignalHistory,
        boolean hasCompetition,
        boolean hasSelectionSide,
        boolean hasStrategy
    ) {
        String persistedCompetition = hasCompetition ? "NULLIF(bi.competition_name, '')" : "NULL";
        String historyCompetition = joinSignalHistory ? "NULLIF(sh.competition_name, '')" : "NULL";
        String competitionSelect = "COALESCE(%s, %s, 'N/A') AS competition_name"
            .formatted(persistedCompetition, historyCompetition);
        String selectionSideSelect = hasSelectionSide
            ? "COALESCE(NULLIF(bi.selection_side, ''), 'UNKNOWN') AS selection_side"
            : "'UNKNOWN' AS selection_side";
        String strategySelect = hasStrategy
            ? "COALESCE(NULLIF(bi.strategy_name, ''), 'N/A') AS strategy_name"
            : "'N/A' AS strategy_name";
        String join = joinSignalHistory
            ? """
                LEFT JOIN (
                    SELECT bet_intent_id, MAX(competition_name) AS competition_name
                    FROM signal_history
                    WHERE bet_intent_id IS NOT NULL
                    GROUP BY bet_intent_id
                ) sh ON sh.bet_intent_id = bi.id
                """
            : "";
        return """
            SELECT
                bi.id,
                bi.exchange,
                bi.market_id,
                bi.selection_id,
                bi.event_name,
                bi.market_name,
                bi.runner_name,
                %s,
                %s,
                %s,
                bi.odds,
                bi.selected_stake,
                bi.available_balance,
                bi.effective_available_balance,
                bi.balance_snapshot_at,
                bi.settlement_result,
                bi.realized_profit_loss,
                bi.stage,
                bi.created_at,
                bi.settled_at,
                bi.updated_at
            FROM bet_intents bi
            %s
            ORDER BY bi.created_at ASC, bi.id ASC
            """.formatted(competitionSelect, selectionSideSelect, strategySelect, join);
    }

    private static RealBettingReportRow map(ResultSet resultSet) throws SQLException {
        return new RealBettingReportRow(
            resultSet.getString("id"),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getLong("selection_id"),
            resultSet.getString("event_name"),
            resultSet.getString("market_name"),
            resultSet.getString("runner_name"),
            selectionSide(resultSet.getString("selection_side")),
            resultSet.getString("competition_name"),
            resultSet.getString("strategy_name"),
            decimal(resultSet, "odds"),
            decimal(resultSet, "selected_stake"),
            decimal(resultSet, "available_balance"),
            decimal(resultSet, "effective_available_balance"),
            instant(resultSet, "balance_snapshot_at"),
            settlement(resultSet.getString("settlement_result")),
            decimal(resultSet, "realized_profit_loss"),
            stage(resultSet.getString("stage")),
            instant(resultSet, "created_at"),
            instant(resultSet, "settled_at"),
            instant(resultSet, "updated_at")
        );
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, null)) {
            return resultSet.next();
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, table, column)) {
            return resultSet.next();
        }
    }

    private static Instant instant(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static BetIntentStage stage(String value) {
        return value == null || value.isBlank() ? null : BetIntentStage.valueOf(value);
    }

    private static BetSettlementResult settlement(String value) {
        return value == null || value.isBlank() ? null : BetSettlementResult.valueOf(value);
    }

    private static SelectionSide selectionSide(String value) {
        return value == null || value.isBlank() ? SelectionSide.UNKNOWN : SelectionSide.valueOf(value);
    }
}
