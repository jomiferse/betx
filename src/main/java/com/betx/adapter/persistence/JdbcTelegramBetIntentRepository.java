package com.betx.adapter.persistence;

import com.betx.application.port.out.TelegramBetIntentRepository;
import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** JDBC-backed SQLite repository for live bet confirmations. */
@Component
public class JdbcTelegramBetIntentRepository implements TelegramBetIntentRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";
    private static final String LAST_UPDATE_ID_KEY = "telegram_last_processed_update_id";

    private final String databasePath;

    public JdbcTelegramBetIntentRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcTelegramBetIntentRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public Optional<TelegramBetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM telegram_bet_intents
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
    public Optional<TelegramBetIntent> findLatestByKeySince(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        Instant since
    ) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM telegram_bet_intents
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
    public Optional<TelegramBetIntent> findById(String databasePath, String id) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM telegram_bet_intents
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
    public List<TelegramBetIntent> listRecent(String databasePath, int limit) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM telegram_bet_intents
                ORDER BY updated_at DESC
                LIMIT ?
                """)) {
                statement.setInt(1, Math.max(1, limit));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<TelegramBetIntent> intents = new java.util.ArrayList<>();
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
    public long countByStages(String databasePath, List<TelegramBetIntentStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return 0L;
        }
        String placeholders = stages.stream().map(stage -> "?").collect(Collectors.joining(", "));
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS total
                FROM telegram_bet_intents
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
    public BigDecimal sumSelectedStakeByStageSince(String databasePath, TelegramBetIntentStage stage, Instant since) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT selected_stake
                FROM telegram_bet_intents
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
    public void save(String databasePath, TelegramBetIntent intent) {
        upsert(databasePath, intent, false);
    }

    @Override
    public void update(String databasePath, TelegramBetIntent intent) {
        upsert(databasePath, intent, true);
    }

    @Override
    public long loadLastProcessedUpdateId(String databasePath) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state_value
                FROM telegram_state
                WHERE state_key = ?
                LIMIT 1
                """)) {
                statement.setString(1, LAST_UPDATE_ID_KEY);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return 0L;
                    }
                    return Long.parseLong(resultSet.getString("state_value"));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read Telegram state.", exc);
        }
    }

    @Override
    public void saveLastProcessedUpdateId(String databasePath, long updateId) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO telegram_state (state_key, state_value)
                VALUES (?, ?)
                ON CONFLICT(state_key) DO UPDATE SET state_value = excluded.state_value
                """)) {
                statement.setString(1, LAST_UPDATE_ID_KEY);
                statement.setString(2, String.valueOf(updateId));
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save Telegram state.", exc);
        }
    }

    private void upsert(String databasePath, TelegramBetIntent intent, boolean update) {
        try (Connection connection = connection(databasePath)) {
            ensureSchema(connection);
            String sql = update ? """
                UPDATE telegram_bet_intents
                SET exchange = ?, market_id = ?, selection_id = ?, event_name = ?, market_name = ?, runner_name = ?,
                    reason = ?, odds = ?, max_stake = ?, available_balance = ?, selected_stake = ?, stage = ?,
                    result_message = ?, created_at = ?, updated_at = ?
                WHERE id = ?
                """ : """
                INSERT INTO telegram_bet_intents (
                    id, exchange, market_id, selection_id, event_name, market_name, runner_name,
                    reason, odds, max_stake, available_balance, selected_stake, stage, result_message, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private void bindIntentInsert(PreparedStatement statement, TelegramBetIntent intent) throws SQLException {
        statement.setString(1, intent.id());
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
        statement.setString(15, intent.createdAt().toString());
        statement.setString(16, intent.updatedAt().toString());
    }

    private void bindIntentUpdate(PreparedStatement statement, TelegramBetIntent intent) throws SQLException {
        statement.setString(1, intent.exchange());
        statement.setString(2, intent.marketId());
        statement.setLong(3, intent.selectionId());
        statement.setString(4, intent.eventName());
        statement.setString(5, intent.marketName());
        statement.setString(6, intent.runnerName());
        statement.setString(7, intent.reason());
        setDecimal(statement, 8, intent.odds());
        setDecimal(statement, 9, intent.maxStake());
        setDecimal(statement, 10, intent.availableBalance());
        setDecimal(statement, 11, intent.selectedStake());
        statement.setString(12, intent.stage().name());
        statement.setString(13, intent.resultMessage());
        statement.setString(14, intent.createdAt().toString());
        statement.setString(15, intent.updatedAt().toString());
        statement.setString(16, intent.id());
    }

    private Connection connection(String path) throws SQLException {
        Path database = Path.of(path == null || path.isBlank() ? databasePath : path);
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

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS telegram_bet_intents (
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
                    result_message TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            addColumnIfMissing(connection, "telegram_bet_intents", "result_message", "TEXT");
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_telegram_bet_intents_active
                ON telegram_bet_intents(exchange, market_id, selection_id, stage, created_at DESC)
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS telegram_state (
                    state_key TEXT PRIMARY KEY,
                    state_value TEXT NOT NULL
                )
                """);
        }
    }

    private TelegramBetIntent map(ResultSet resultSet) throws SQLException {
        return new TelegramBetIntent(
            resultSet.getString("id"),
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
            TelegramBetIntentStage.valueOf(resultSet.getString("stage")),
            Instant.parse(resultSet.getString("created_at")),
            Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private void bindStages(PreparedStatement statement, List<TelegramBetIntentStage> stages) throws SQLException {
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
