package com.betx.adapter.persistence;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** JDBC-backed SQLite repository for live bet confirmations. */
@Component
public class JdbcBetIntentRepository implements BetIntentRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcBetIntentRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcBetIntentRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND market_id = ? AND selection_id = ? AND stage IN ('AWAITING_CONFIRMATION', 'AWAITING_STAKE')
                ORDER BY created_at DESC
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
            throw new IllegalStateException("Could not read live bet intent.", exc);
        }
    }

    @Override
    public Optional<BetIntent> findLatestByKeySince(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        Instant since
    ) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND market_id = ? AND selection_id = ? AND updated_at >= ?
                ORDER BY updated_at DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                statement.setLong(3, selectionId);
                statement.setString(4, since.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read recent live bet intent.", exc);
        }
    }

    @Override
    public Optional<BetIntent> findById(String databasePath, String id) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE id = ?
                LIMIT 1
                """)) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read live bet intent.", exc);
        }
    }

    @Override
    public List<BetIntent> listRecent(String databasePath, int limit) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                ORDER BY updated_at DESC
                LIMIT ?
                """)) {
                statement.setInt(1, Math.max(1, limit));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<BetIntent> intents = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        intents.add(map(resultSet));
                    }
                    return intents;
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list live bet intents.", exc);
        }
    }

    @Override
    public List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        String placeholders = stages.stream().map(stage -> "?").collect(Collectors.joining(", "));
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE stage IN (%s)
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(placeholders))) {
                bindStages(statement, stages);
                statement.setInt(stages.size() + 1, Math.max(1, limit));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<BetIntent> intents = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        intents.add(map(resultSet));
                    }
                    return intents;
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list live bet intents by stage.", exc);
        }
    }

    @Override
    public long countByStages(String databasePath, List<BetIntentStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return 0L;
        }
        String placeholders = stages.stream().map(stage -> "?").collect(Collectors.joining(", "));
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS total
                FROM bet_intents
                WHERE stage IN (%s)
                """.formatted(placeholders))) {
                bindStages(statement, stages);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getLong("total") : 0L;
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not count live bet intents.", exc);
        }
    }

    @Override
    public BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT selected_stake
                FROM bet_intents
                WHERE stage = ? AND updated_at >= ? AND selected_stake IS NOT NULL
                """)) {
                statement.setString(1, stage.name());
                statement.setString(2, since.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    BigDecimal total = BigDecimal.ZERO;
                    while (resultSet.next()) {
                        total = total.add(new BigDecimal(resultSet.getString("selected_stake")));
                    }
                    return total.setScale(2, RoundingMode.HALF_UP);
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not sum live bet stake.", exc);
        }
    }

    @Override
    public void save(String databasePath, BetIntent intent) {
        upsert(databasePath, intent, false);
    }

    @Override
    public void update(String databasePath, BetIntent intent) {
        upsert(databasePath, intent, true);
    }

    private void upsert(String databasePath, BetIntent intent, boolean update) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            String sql = update ? """
                UPDATE bet_intents
                SET source = ?, exchange = ?, market_id = ?, selection_id = ?, event_name = ?, market_name = ?, runner_name = ?,
                    reason = ?, odds = ?, max_stake = ?, available_balance = ?, selected_stake = ?, stage = ?,
                    result_message = ?, external_order_id = ?, created_at = ?, updated_at = ?
                WHERE id = ?
                """ : """
                INSERT INTO bet_intents (
                    id, source, exchange, market_id, selection_id, event_name, market_name, runner_name,
                    reason, odds, max_stake, available_balance, selected_stake, stage, result_message,
                    external_order_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (update) {
                    bindIntentUpdate(statement, intent);
                } else {
                    bindIntentInsert(statement, intent);
                }
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save live bet intent.", exc);
        }
    }

    private void bindIntentInsert(PreparedStatement statement, BetIntent intent) throws SQLException {
        statement.setString(1, intent.id());
        statement.setString(2, intent.source().name());
        statement.setString(3, intent.exchange());
        statement.setString(4, intent.marketId());
        statement.setLong(5, intent.selectionId());
        statement.setString(6, intent.eventName());
        statement.setString(7, intent.marketName());
        statement.setString(8, intent.runnerName());
        statement.setString(9, intent.reason());
        setDecimal(statement, 10, intent.odds());
        setDecimal(statement, 11, intent.maxStake());
        setDecimal(statement, 12, intent.availableBalance());
        setDecimal(statement, 13, intent.selectedStake());
        statement.setString(14, intent.stage().name());
        statement.setString(15, intent.resultMessage());
        statement.setString(16, intent.externalOrderId());
        statement.setString(17, intent.createdAt().toString());
        statement.setString(18, intent.updatedAt().toString());
    }

    private void bindIntentUpdate(PreparedStatement statement, BetIntent intent) throws SQLException {
        statement.setString(1, intent.source().name());
        statement.setString(2, intent.exchange());
        statement.setString(3, intent.marketId());
        statement.setLong(4, intent.selectionId());
        statement.setString(5, intent.eventName());
        statement.setString(6, intent.marketName());
        statement.setString(7, intent.runnerName());
        statement.setString(8, intent.reason());
        setDecimal(statement, 9, intent.odds());
        setDecimal(statement, 10, intent.maxStake());
        setDecimal(statement, 11, intent.availableBalance());
        setDecimal(statement, 12, intent.selectedStake());
        statement.setString(13, intent.stage().name());
        statement.setString(14, intent.resultMessage());
        statement.setString(15, intent.externalOrderId());
        statement.setString(16, intent.createdAt().toString());
        statement.setString(17, intent.updatedAt().toString());
        statement.setString(18, intent.id());
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
                throw new IllegalStateException("Could not initialize bet intent schema.", exc);
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
                CREATE TABLE IF NOT EXISTS bet_intents (
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
                    result_message TEXT,
                    external_order_id TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            addColumnIfMissing(connection, "bet_intents", "source", "TEXT NOT NULL DEFAULT 'TELEGRAM_CONFIRMATION'");
            addColumnIfMissing(connection, "bet_intents", "result_message", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "external_order_id", "TEXT");
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_intents_active
                ON bet_intents(exchange, market_id, selection_id, stage, created_at DESC)
                """);
        }
    }

    private BetIntent map(ResultSet resultSet) throws SQLException {
        return new BetIntent(
            resultSet.getString("id"),
            BetIntentSource.valueOf(resultSet.getString("source")),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getLong("selection_id"),
            resultSet.getString("event_name"),
            resultSet.getString("market_name"),
            resultSet.getString("runner_name"),
            resultSet.getString("reason"),
            decimal(resultSet, "odds"),
            decimal(resultSet, "max_stake"),
            decimal(resultSet, "available_balance"),
            decimal(resultSet, "selected_stake"),
            resultSet.getString("result_message"),
            resultSet.getString("external_order_id"),
            BetIntentStage.valueOf(resultSet.getString("stage")),
            Instant.parse(resultSet.getString("created_at")),
            Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private void bindStages(PreparedStatement statement, List<BetIntentStage> stages) throws SQLException {
        for (int index = 0; index < stages.size(); index++) {
            statement.setString(index + 1, stages.get(index).name());
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
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
