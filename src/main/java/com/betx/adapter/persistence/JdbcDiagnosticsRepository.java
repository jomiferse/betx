package com.betx.adapter.persistence;

import com.betx.application.BacktestOutcome;
import com.betx.application.DiagnosticsBetRecommendationsSummary;
import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import com.betx.application.DiagnosticsModel.RealBetDiagnosticRow;
import com.betx.application.DiagnosticsPeriod;
import com.betx.application.DiagnosticsRepository;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.BetExecutionStatus;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdbcDiagnosticsRepository implements DiagnosticsRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    @Override
    public DiagnosticsDataset load(String databasePath, Instant from, Instant to) {
        Path database = database(databasePath);
        if (!Files.exists(database)) {
            return new DiagnosticsDataset(List.of(), List.of(), 0, 0, Map.of(), Map.of());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            List<RealBetDiagnosticRow> realBets = tableExists(connection, "bet_intents")
                ? realBets(connection, from, to)
                : List.of();
            List<PaperTrade> paperTrades = tableExists(connection, "paper_trades")
                ? paperTrades(connection, from, to)
                : List.of();
            long marketsScanned = tableExists(connection, "market_snapshots")
                ? countDistinctMarkets(connection, from, to)
                : 0;
            long runnersAnalyzed = tableExists(connection, "paper_signal_evaluations")
                ? countPaperEvaluations(connection, from, to)
                : 0;
            Map<String, Long> recommendations = tableExists(connection, "signal_history")
                ? groupedCount(connection, "signal_history", "recommendation", "observed_at", from, to)
                : Map.of();
            Map<String, Long> rejections = tableExists(connection, "paper_signal_evaluations")
                ? groupedCount(connection, "paper_signal_evaluations", "analyzer_reason", "observed_at", from, to)
                : Map.of();
            DiagnosticsBetRecommendationsSummary betRecommendations = tableExists(connection, "bet_recommendations")
                ? betRecommendations(connection, from, to)
                : DiagnosticsBetRecommendationsSummary.empty();
            return new DiagnosticsDataset(
                realBets,
                paperTrades,
                marketsScanned,
                runnersAnalyzed,
                recommendations,
                rejections,
                betRecommendations
            );
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read diagnostics data.", exc);
        }
    }

    @Override
    public DiagnosticsPeriod findDefaultPeriod(String databasePath) {
        Path database = database(databasePath);
        if (!Files.exists(database)) {
            return new DiagnosticsPeriod(null, null);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            List<Instant> instants = new ArrayList<>();
            if (tableExists(connection, "bet_intents")) {
                addBounds(connection, instants, "bet_intents", List.of("created_at", "updated_at", "settled_at"));
            }
            if (tableExists(connection, "paper_trades")) {
                addBounds(connection, instants, "paper_trades", List.of(
                    "recommendation_timestamp",
                    "execution_timestamp",
                    "settlement_timestamp"
                ));
            }
            if (instants.isEmpty()) {
                return new DiagnosticsPeriod(null, null);
            }
            instants.sort(Instant::compareTo);
            return new DiagnosticsPeriod(instants.getFirst(), instants.getLast());
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read diagnostics default period.", exc);
        }
    }

    private static List<RealBetDiagnosticRow> realBets(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = """
            SELECT id, source, exchange, market_id, selection_id, event_name, market_name, runner_name,
                   competition_name, selection_side, strategy_name, odds, selected_stake, stage,
                   settlement_result, realized_profit_loss, external_order_id, created_at, settled_at, updated_at,
                   available_balance, effective_available_balance, reserved_balance, balance_snapshot_at,
                   %s AS evaluation_id, %s AS recommendation_id, %s AS recommended_at, %s AS recommended_odds,
                   %s AS order_submitted_at, %s AS order_response_at, %s AS order_accepted_at, %s AS executed_at,
                   %s AS requested_odds, %s AS average_executed_odds, %s AS requested_stake, %s AS matched_stake,
                   %s AS remaining_stake, %s AS execution_status
            FROM bet_intents
            WHERE (%s)
            ORDER BY created_at ASC, id ASC
            """.formatted(
                columnOrNull(connection, "bet_intents", "evaluation_id"),
                columnOrNull(connection, "bet_intents", "recommendation_id"),
                columnOrNull(connection, "bet_intents", "recommended_at"),
                columnOrNull(connection, "bet_intents", "recommended_odds"),
                columnOrNull(connection, "bet_intents", "order_submitted_at"),
                columnOrNull(connection, "bet_intents", "order_response_at"),
                columnOrNull(connection, "bet_intents", "order_accepted_at"),
                columnOrNull(connection, "bet_intents", "executed_at"),
                columnOrNull(connection, "bet_intents", "requested_odds"),
                columnOrNull(connection, "bet_intents", "average_executed_odds"),
                columnOrNull(connection, "bet_intents", "requested_stake"),
                columnOrNull(connection, "bet_intents", "matched_stake"),
                columnOrNull(connection, "bet_intents", "remaining_stake"),
                columnOrNull(connection, "bet_intents", "execution_status"),
                periodPredicate(List.of("created_at", "updated_at", "settled_at"))
            );
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RealBetDiagnosticRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new RealBetDiagnosticRow(
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
                        stage(resultSet.getString("stage")),
                        settlement(resultSet.getString("settlement_result")),
                        decimal(resultSet, "realized_profit_loss"),
                        resultSet.getString("external_order_id"),
                        instant(resultSet, "created_at"),
                        instant(resultSet, "settled_at"),
                        instant(resultSet, "updated_at"),
                        decimal(resultSet, "available_balance"),
                        decimal(resultSet, "effective_available_balance"),
                        decimal(resultSet, "reserved_balance"),
                        instant(resultSet, "balance_snapshot_at"),
                        resultSet.getString("evaluation_id"),
                        resultSet.getString("recommendation_id"),
                        instant(resultSet, "recommended_at"),
                        decimal(resultSet, "recommended_odds"),
                        instant(resultSet, "order_submitted_at"),
                        instant(resultSet, "order_response_at"),
                        instant(resultSet, "order_accepted_at"),
                        instant(resultSet, "executed_at"),
                        decimal(resultSet, "requested_odds"),
                        decimal(resultSet, "average_executed_odds"),
                        decimal(resultSet, "requested_stake"),
                        decimal(resultSet, "matched_stake"),
                        decimal(resultSet, "remaining_stake"),
                        executionStatus(resultSet.getString("execution_status"))
                    ));
                }
                return rows;
            }
        }
    }

    private static List<PaperTrade> paperTrades(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = """
            SELECT *
            FROM paper_trades
            WHERE (%s)
            ORDER BY recommendation_timestamp ASC, id ASC
            """.formatted(periodPredicate(List.of("recommendation_timestamp", "execution_timestamp", "settlement_timestamp")));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PaperTrade> rows = new ArrayList<>();
                while (resultSet.next()) {
            rows.add(new PaperTrade(
                        resultSet.getString("id"),
                        resultSet.getString("exchange"),
                        resultSet.getString("market_id"),
                        resultSet.getLong("selection_id"),
                        resultSet.getString("event_name"),
                        resultSet.getString("market_name"),
                        resultSet.getString("league"),
                        instant(resultSet, "market_start_time"),
                        resultSet.getString("runner_name"),
                        side(resultSet.getString("side")),
                        PaperTradeStatus.valueOf(resultSet.getString("status")),
                        instant(resultSet, "recommendation_timestamp"),
                        decimal(resultSet, "available_back_odds"),
                        decimal(resultSet, "requested_odds"),
                        instant(resultSet, "execution_timestamp"),
                        decimal(resultSet, "execution_odds"),
                        resultSet.getInt("matched") == 1,
                        instant(resultSet, "closing_timestamp"),
                        decimal(resultSet, "closing_odds"),
                        instant(resultSet, "settlement_timestamp"),
                        outcome(resultSet.getString("result")),
                        decimal(resultSet, "stake"),
                        decimal(resultSet, "gross_pnl"),
                        decimal(resultSet, "commission"),
                        decimal(resultSet, "net_pnl"),
                        decimal(resultSet, "decimal_clv_ratio"),
                        decimal(resultSet, "implied_probability_change"),
                        resultSet.getInt("paper_mode") == 1,
                        hasColumn(connection, "paper_trades", "recommendation_id") ? resultSet.getString("recommendation_id") : null
                    ));
                }
                return rows;
            }
        }
    }

    private static long countDistinctMarkets(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(DISTINCT exchange || '|' || market_id) AS total
            FROM market_snapshots
            WHERE (%s)
            """.formatted(periodPredicate(List.of("observed_at"))))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countPaperEvaluations(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM paper_signal_evaluations
            WHERE (%s)
            """.formatted(periodPredicate(List.of("observed_at"))))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static Map<String, Long> groupedCount(
        Connection connection,
        String table,
        String groupColumn,
        String timestampColumn,
        Instant from,
        Instant to
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(NULLIF(%s, ''), 'UNKNOWN') AS name, COUNT(*) AS total
            FROM %s
            WHERE (%s)
            GROUP BY COALESCE(NULLIF(%s, ''), 'UNKNOWN')
            ORDER BY total DESC
            """.formatted(groupColumn, table, periodPredicate(List.of(timestampColumn)), groupColumn))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, Long> values = new LinkedHashMap<>();
                while (resultSet.next()) {
                    values.put(resultSet.getString("name"), resultSet.getLong("total"));
                }
                return values;
            }
        }
    }

    private static DiagnosticsBetRecommendationsSummary betRecommendations(
        Connection connection,
        Instant from,
        Instant to
    ) throws SQLException {
        long total = countRecommendations(connection, from, to, "1 = 1");
        long withEvaluationId = countRecommendations(connection, from, to, "evaluation_id IS NOT NULL AND evaluation_id <> ''");
        long withStrategyName = countRecommendations(connection, from, to, "strategy_name IS NOT NULL AND strategy_name <> ''");
        long withSelectionSide = countRecommendations(connection, from, to, "selection_side IS NOT NULL AND selection_side <> '' AND selection_side <> 'UNKNOWN'");
        long orphanRecommendations = countRecommendations(connection, from, to, "evaluation_id IS NULL OR evaluation_id = ''");
        return new DiagnosticsBetRecommendationsSummary(
            total,
            withEvaluationId,
            withStrategyName,
            withSelectionSide,
            groupedCount(connection, "bet_recommendations", "strategy_name", "recommended_at", from, to),
            groupedCount(connection, "bet_recommendations", "selection_side", "recommended_at", from, to),
            groupedCount(connection, "bet_recommendations", "competition_name", "recommended_at", from, to),
            total,
            orphanRecommendations
        );
    }

    private static long countRecommendations(
        Connection connection,
        Instant from,
        Instant to,
        String extraPredicate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM bet_recommendations
            WHERE (%s) AND (%s)
            """.formatted(periodPredicate(List.of("recommended_at", "created_at")), extraPredicate))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static void addBounds(Connection connection, List<Instant> instants, String table, List<String> columns) throws SQLException {
        for (String column : columns) {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT MIN(" + column + ") AS min_value, MAX(" + column + ") AS max_value FROM " + table)) {
                if (resultSet.next()) {
                    addInstant(instants, resultSet.getString("min_value"));
                    addInstant(instants, resultSet.getString("max_value"));
                }
            }
        }
    }

    private static void addInstant(List<Instant> instants, String value) {
        if (value != null && !value.isBlank()) {
            instants.add(Instant.parse(value));
        }
    }

    private static String periodPredicate(List<String> columns) {
        return columns.stream()
            .map(column -> "(" + column + " IS NOT NULL AND (? IS NULL OR " + column + " >= ?) AND (? IS NULL OR " + column + " <= ?))")
            .collect(java.util.stream.Collectors.joining(" OR "));
    }

    private static void bindPeriod(PreparedStatement statement, Instant from, Instant to, int timestampColumns) throws SQLException {
        int index = 1;
        for (int ignored = 0; ignored < timestampColumns; ignored++) {
            statement.setString(index++, from == null ? null : from.toString());
            statement.setString(index++, from == null ? null : from.toString());
            statement.setString(index++, to == null ? null : to.toString());
            statement.setString(index++, to == null ? null : to.toString());
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, null)) {
            return resultSet.next();
        }
    }

    private static String columnOrNull(Connection connection, String table, String column) throws SQLException {
        return hasColumn(connection, table, column) ? column : "NULL";
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, table, column)) {
            return resultSet.next();
        }
    }

    private static Path database(String databasePath) {
        return Path.of(databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath)
            .toAbsolutePath()
            .normalize();
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

    private static BetExecutionStatus executionStatus(String value) {
        return value == null || value.isBlank() ? null : BetExecutionStatus.valueOf(value);
    }

    private static SelectionSide selectionSide(String value) {
        return value == null || value.isBlank() ? SelectionSide.UNKNOWN : SelectionSide.valueOf(value);
    }

    private static BetSide side(String value) {
        return value == null || value.isBlank() ? BetSide.BACK : BetSide.valueOf(value);
    }

    private static BacktestOutcome outcome(String value) {
        return value == null || value.isBlank() ? null : BacktestOutcome.valueOf(value);
    }
}
