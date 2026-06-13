package com.betx.adapter.persistence;

import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** JDBC-backed SQLite repository for normalized market snapshots. */
@Component
public class JdbcMarketSnapshotRepository implements MarketSnapshotRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcMarketSnapshotRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcMarketSnapshotRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    public Optional<ObservedMarketSnapshot> findLatest(String exchange, String marketId, long selectionId) {
        return findLatest(databasePath, exchange, marketId, selectionId);
    }

    public List<ObservedMarketSnapshot> findRecent(String exchange, String marketId, long selectionId, int limit) {
        return findRecent(databasePath, exchange, marketId, selectionId, limit);
    }

    public void save(ObservedMarketSnapshot snapshot) {
        save(databasePath, snapshot);
    }

    @Override
    public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT observed_at, exchange, market_id, market_name, event_name, competition_name, market_start_time,
                       runner_name,
                       selection_id, best_back_price, best_lay_price, spread, liquidity
                FROM market_snapshots
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
            throw new IllegalStateException("Could not read market snapshot.", exc);
        }
    }

    @Override
    public List<ObservedMarketSnapshot> findRecent(String databasePath, String exchange, String marketId, long selectionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT observed_at, exchange, market_id, market_name, event_name, competition_name, market_start_time,
                       runner_name,
                       selection_id, best_back_price, best_lay_price, spread, liquidity
                FROM market_snapshots
                WHERE exchange = ? AND market_id = ? AND selection_id = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT ?
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                statement.setLong(3, selectionId);
                statement.setInt(4, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ObservedMarketSnapshot> snapshots = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        snapshots.add(map(resultSet));
                    }
                    return snapshots;
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read recent market snapshots.", exc);
        }
    }

    @Override
    public void save(String databasePath, ObservedMarketSnapshot observed) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            MarketSnapshot snapshot = observed.snapshot();
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO market_snapshots (
                    observed_at, exchange, market_id, market_name, event_name, competition_name, market_start_time,
                    runner_name,
                    selection_id, best_back_price, best_lay_price, spread, liquidity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                statement.setString(1, observed.observedAt().toString());
                statement.setString(2, snapshot.exchange());
                statement.setString(3, snapshot.marketId());
                statement.setString(4, snapshot.marketName());
                statement.setString(5, snapshot.eventName());
                statement.setString(6, snapshot.competitionName());
                statement.setString(7, snapshot.marketStartTime() == null ? null : snapshot.marketStartTime().toString());
                statement.setString(8, snapshot.runnerName());
                statement.setLong(9, snapshot.selectionId());
                setDecimal(statement, 10, snapshot.bestBackPrice());
                setDecimal(statement, 11, snapshot.bestLayPrice());
                setDecimal(statement, 12, snapshot.spread());
                setDecimal(statement, 13, snapshot.liquidity());
                statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save market snapshot.", exc);
        }
    }

    @Override
    public int deleteExpiredMarkets(String databasePath, Instant marketStartTimeBefore) {
        if (marketStartTimeBefore == null) {
            return 0;
        }
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM market_snapshots
                WHERE market_start_time IS NOT NULL AND market_start_time < ?
                """)) {
                statement.setString(1, marketStartTimeBefore.toString());
                return statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not delete expired market snapshots.", exc);
        }
    }

    @Override
    public int deleteMarket(String databasePath, String exchange, String marketId) {
        if (exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank()) {
            return 0;
        }
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM market_snapshots
                WHERE exchange = ? AND market_id = ?
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                return statement.executeUpdate();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not delete market snapshots.", exc);
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
                throw new IllegalStateException("Could not initialize market snapshot schema.", exc);
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
                CREATE TABLE IF NOT EXISTS market_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    market_name TEXT,
                    event_name TEXT,
                    competition_name TEXT,
                    market_start_time TEXT,
                    runner_name TEXT,
                    selection_id INTEGER NOT NULL,
                    best_back_price TEXT,
                    best_lay_price TEXT,
                    spread TEXT,
                    liquidity TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_market_snapshots_runner_latest
                ON market_snapshots(exchange, market_id, selection_id, observed_at DESC)
                """);
            addColumnIfMissing(connection, "runner_name", "TEXT");
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String type) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "market_snapshots", columnName)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE market_snapshots ADD COLUMN " + columnName + " " + type);
        }
    }

    private ObservedMarketSnapshot map(ResultSet resultSet) throws SQLException {
        String marketStartTime = resultSet.getString("market_start_time");
        return new ObservedMarketSnapshot(
            Instant.parse(resultSet.getString("observed_at")),
            new MarketSnapshot(
                resultSet.getString("exchange"),
                resultSet.getString("market_id"),
                resultSet.getString("market_name"),
                resultSet.getString("event_name"),
                resultSet.getString("competition_name"),
                marketStartTime == null ? null : Instant.parse(marketStartTime),
                resultSet.getLong("selection_id"),
                resultSet.getString("runner_name"),
                decimal(resultSet, "best_back_price"),
                decimal(resultSet, "best_lay_price"),
                decimal(resultSet, "spread"),
                decimal(resultSet, "liquidity")
            )
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
