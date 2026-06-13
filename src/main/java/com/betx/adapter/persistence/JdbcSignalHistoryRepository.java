package com.betx.adapter.persistence;

import com.betx.application.MatchIntelligenceDecision;
import com.betx.application.SignalHistoryEntry;
import com.betx.application.SignalHistoryKey;
import com.betx.application.port.out.SignalHistoryRepository;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.order.BetIntent;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** JDBC-backed SQLite repository for compact long-lived signal history. */
@Component
public class JdbcSignalHistoryRepository implements SignalHistoryRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";
    private static final String TABLE = "signal_history";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcSignalHistoryRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcSignalHistoryRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public void saveDecision(String databasePath, SignalHistoryEntry entry) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signal_history (
                    observed_at, exchange, market_id, selection_id,
                    event_name, market_name, runner_name, competition_name, market_start_time,
                    recommendation, score, confidence_label, reason,
                    best_back_price, best_lay_price, spread, liquidity,
                    back_percentage_delta, lay_percentage_delta, liquidity_percentage_delta,
                    intelligence_decision, intelligence_confidence, intelligence_summary,
                    bet_intent_id, external_order_id, order_stage, selected_stake, result_message, realized_profit_loss
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(exchange, market_id, selection_id, observed_at) DO UPDATE SET
                    event_name = excluded.event_name,
                    market_name = excluded.market_name,
                    runner_name = excluded.runner_name,
                    competition_name = excluded.competition_name,
                    market_start_time = excluded.market_start_time,
                    recommendation = excluded.recommendation,
                    score = excluded.score,
                    confidence_label = excluded.confidence_label,
                    reason = excluded.reason,
                    best_back_price = excluded.best_back_price,
                    best_lay_price = excluded.best_lay_price,
                    spread = excluded.spread,
                    liquidity = excluded.liquidity,
                    back_percentage_delta = excluded.back_percentage_delta,
                    lay_percentage_delta = excluded.lay_percentage_delta,
                    liquidity_percentage_delta = excluded.liquidity_percentage_delta,
                    intelligence_decision = excluded.intelligence_decision,
                    intelligence_confidence = excluded.intelligence_confidence,
                    intelligence_summary = excluded.intelligence_summary
                """)) {
                bindEntry(statement, entry);
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save signal history.", exc);
        }
    }

    @Override
    public void linkIntent(String databasePath, SignalHistoryKey key, BetIntent intent) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE signal_history
                SET bet_intent_id = ?, external_order_id = ?, order_stage = ?, selected_stake = ?, result_message = ?
                WHERE exchange = ? AND market_id = ? AND selection_id = ? AND observed_at = ?
                """)) {
                bindIntentFields(statement, intent, 1);
                bindKey(statement, key, 6);
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not link signal history intent.", exc);
        }
    }

    @Override
    public void updateOrderState(String databasePath, BetIntent intent) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE signal_history
                SET bet_intent_id = ?, external_order_id = ?, order_stage = ?, selected_stake = ?, result_message = ?
                WHERE bet_intent_id = ?
                """)) {
                bindIntentFields(statement, intent, 1);
                statement.setString(6, intent.id());
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not update signal history order state.", exc);
        }
    }

    public Optional<SignalHistoryEntry> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM signal_history
                WHERE exchange = ? AND market_id = ? AND selection_id = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                statement.setLong(3, selectionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read signal history.", exc);
        }
    }

    public long count(String databasePath) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS total FROM signal_history");
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0L;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not count signal history.", exc);
        }
    }

    private void ensureSchemaInitialized(String path) {
        String resolvedPath = resolvedDatabasePath(path);
        if (initializedDatabases.contains(resolvedPath)) {
            return;
        }
        synchronized (initializedDatabases) {
            if (initializedDatabases.contains(resolvedPath)) {
                return;
            }
            try (Connection connection = connection(resolvedPath)) {
                ensureSchema(connection);
                initializedDatabases.add(resolvedPath);
            } catch (SQLException exc) {
                throw new IllegalStateException("Could not initialize signal history schema.", exc);
            }
        }
    }

    private Connection connection(String path) throws SQLException {
        Path database = Path.of(resolvedDatabasePath(path));
        Path parent = database.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (java.io.IOException exc) {
                throw new IllegalStateException("Could not create data directory: " + parent, exc);
            }
        }
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private String resolvedDatabasePath(String path) {
        return Path.of(path == null || path.isBlank() ? databasePath : path)
            .toAbsolutePath()
            .normalize()
            .toString();
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS signal_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    event_name TEXT,
                    market_name TEXT,
                    runner_name TEXT,
                    competition_name TEXT,
                    market_start_time TEXT,
                    recommendation TEXT NOT NULL,
                    score INTEGER NOT NULL DEFAULT 0,
                    confidence_label TEXT,
                    reason TEXT,
                    best_back_price TEXT,
                    best_lay_price TEXT,
                    spread TEXT,
                    liquidity TEXT,
                    back_percentage_delta TEXT,
                    lay_percentage_delta TEXT,
                    liquidity_percentage_delta TEXT,
                    intelligence_decision TEXT,
                    intelligence_confidence INTEGER,
                    intelligence_summary TEXT,
                    bet_intent_id TEXT,
                    external_order_id TEXT,
                    order_stage TEXT,
                    selected_stake TEXT,
                    result_message TEXT,
                    realized_profit_loss TEXT
                )
                """);
            addColumnIfMissing(connection, "event_name", "TEXT");
            addColumnIfMissing(connection, "market_name", "TEXT");
            addColumnIfMissing(connection, "runner_name", "TEXT");
            addColumnIfMissing(connection, "competition_name", "TEXT");
            addColumnIfMissing(connection, "market_start_time", "TEXT");
            addColumnIfMissing(connection, "score", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "confidence_label", "TEXT");
            addColumnIfMissing(connection, "reason", "TEXT");
            addColumnIfMissing(connection, "best_back_price", "TEXT");
            addColumnIfMissing(connection, "best_lay_price", "TEXT");
            addColumnIfMissing(connection, "spread", "TEXT");
            addColumnIfMissing(connection, "liquidity", "TEXT");
            addColumnIfMissing(connection, "back_percentage_delta", "TEXT");
            addColumnIfMissing(connection, "lay_percentage_delta", "TEXT");
            addColumnIfMissing(connection, "liquidity_percentage_delta", "TEXT");
            addColumnIfMissing(connection, "intelligence_decision", "TEXT");
            addColumnIfMissing(connection, "intelligence_confidence", "INTEGER");
            addColumnIfMissing(connection, "intelligence_summary", "TEXT");
            addColumnIfMissing(connection, "bet_intent_id", "TEXT");
            addColumnIfMissing(connection, "external_order_id", "TEXT");
            addColumnIfMissing(connection, "order_stage", "TEXT");
            addColumnIfMissing(connection, "selected_stake", "TEXT");
            addColumnIfMissing(connection, "result_message", "TEXT");
            addColumnIfMissing(connection, "realized_profit_loss", "TEXT");
            statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_signal_history_unique_decision
                ON signal_history(exchange, market_id, selection_id, observed_at)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_signal_history_market_start_time
                ON signal_history(market_start_time)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_signal_history_recommendation
                ON signal_history(recommendation)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_signal_history_order_stage
                ON signal_history(order_stage)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_signal_history_external_order_id
                ON signal_history(external_order_id)
                """);
        }
    }

    private void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, TABLE, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE signal_history ADD COLUMN " + column + " " + definition);
        }
    }

    private void bindEntry(PreparedStatement statement, SignalHistoryEntry entry) throws SQLException {
        statement.setString(1, entry.observedAt().toString());
        statement.setString(2, entry.exchange());
        statement.setString(3, entry.marketId());
        statement.setLong(4, entry.selectionId());
        statement.setString(5, entry.eventName());
        statement.setString(6, entry.marketName());
        statement.setString(7, entry.runnerName());
        statement.setString(8, entry.competitionName());
        statement.setString(9, entry.marketStartTime() == null ? null : entry.marketStartTime().toString());
        statement.setString(10, entry.recommendation().name());
        statement.setInt(11, entry.score());
        statement.setString(12, entry.confidenceLabel());
        statement.setString(13, entry.reason());
        setDecimal(statement, 14, entry.bestBackPrice());
        setDecimal(statement, 15, entry.bestLayPrice());
        setDecimal(statement, 16, entry.spread());
        setDecimal(statement, 17, entry.liquidity());
        setDecimal(statement, 18, entry.backPercentageDelta());
        setDecimal(statement, 19, entry.layPercentageDelta());
        setDecimal(statement, 20, entry.liquidityPercentageDelta());
        statement.setString(21, entry.intelligenceDecision() == null ? null : entry.intelligenceDecision().name());
        if (entry.intelligenceConfidence() == null) {
            statement.setObject(22, null);
        } else {
            statement.setInt(22, entry.intelligenceConfidence());
        }
        statement.setString(23, entry.intelligenceSummary());
        statement.setString(24, entry.betIntentId());
        statement.setString(25, entry.externalOrderId());
        statement.setString(26, entry.orderStage());
        setDecimal(statement, 27, entry.selectedStake());
        statement.setString(28, entry.resultMessage());
        setDecimal(statement, 29, entry.realizedProfitLoss());
    }

    private void bindIntentFields(PreparedStatement statement, BetIntent intent, int startIndex) throws SQLException {
        statement.setString(startIndex, intent.id());
        statement.setString(startIndex + 1, intent.externalOrderId());
        statement.setString(startIndex + 2, intent.stage().name());
        setDecimal(statement, startIndex + 3, intent.selectedStake());
        statement.setString(startIndex + 4, intent.resultMessage());
    }

    private void bindKey(PreparedStatement statement, SignalHistoryKey key, int startIndex) throws SQLException {
        statement.setString(startIndex, key.exchange());
        statement.setString(startIndex + 1, key.marketId());
        statement.setLong(startIndex + 2, key.selectionId());
        statement.setString(startIndex + 3, key.observedAt().toString());
    }

    private SignalHistoryEntry map(ResultSet resultSet) throws SQLException {
        String marketStartTime = resultSet.getString("market_start_time");
        String intelligenceDecision = resultSet.getString("intelligence_decision");
        Integer intelligenceConfidence = resultSet.getObject("intelligence_confidence") == null
            ? null
            : resultSet.getInt("intelligence_confidence");
        return new SignalHistoryEntry(
            Instant.parse(resultSet.getString("observed_at")),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getLong("selection_id"),
            resultSet.getString("event_name"),
            resultSet.getString("market_name"),
            resultSet.getString("runner_name"),
            resultSet.getString("competition_name"),
            marketStartTime == null ? null : Instant.parse(marketStartTime),
            RecommendationType.valueOf(resultSet.getString("recommendation")),
            resultSet.getInt("score"),
            resultSet.getString("confidence_label"),
            resultSet.getString("reason"),
            decimal(resultSet, "best_back_price"),
            decimal(resultSet, "best_lay_price"),
            decimal(resultSet, "spread"),
            decimal(resultSet, "liquidity"),
            decimal(resultSet, "back_percentage_delta"),
            decimal(resultSet, "lay_percentage_delta"),
            decimal(resultSet, "liquidity_percentage_delta"),
            intelligenceDecision == null ? null : MatchIntelligenceDecision.valueOf(intelligenceDecision),
            intelligenceConfidence,
            resultSet.getString("intelligence_summary"),
            resultSet.getString("bet_intent_id"),
            resultSet.getString("external_order_id"),
            resultSet.getString("order_stage"),
            decimal(resultSet, "selected_stake"),
            resultSet.getString("result_message"),
            decimal(resultSet, "realized_profit_loss")
        );
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setString(index, null);
        } else {
            statement.setString(index, value.toPlainString());
        }
    }

    private BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null ? null : new BigDecimal(value);
    }
}
