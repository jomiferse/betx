package com.betx.adapter.persistence;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.application.port.out.PaperTradeRepository;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** SQLite repository for auditable read-only paper-trade lifecycle records. */
@Component
public class JdbcPaperTradeRepository implements PaperTradeRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcPaperTradeRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcPaperTradeRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM paper_trades
                 WHERE exchange = ? AND market_id = ? AND selection_id = ?
                 LIMIT 1
                 """)) {
            statement.setString(1, exchange);
            statement.setString(2, marketId);
            statement.setLong(3, selectionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read paper trade.", exc);
        }
    }

    @Override
    public void upsert(String databasePath, PaperTrade trade) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO paper_trades (
                     id, exchange, market_id, selection_id, event_name, market_name, league, market_start_time,
                     runner_name, status, recommendation_timestamp, available_back_odds, requested_odds,
                     execution_timestamp, execution_odds, matched, closing_timestamp, closing_odds,
                     settlement_timestamp, result, stake, gross_pnl, commission, net_pnl,
                     decimal_clv_ratio, implied_probability_change, paper_mode
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(id) DO UPDATE SET
                     event_name = excluded.event_name,
                     market_name = excluded.market_name,
                     league = excluded.league,
                     market_start_time = excluded.market_start_time,
                     runner_name = excluded.runner_name,
                     status = excluded.status,
                     recommendation_timestamp = excluded.recommendation_timestamp,
                     available_back_odds = excluded.available_back_odds,
                     requested_odds = excluded.requested_odds,
                     execution_timestamp = excluded.execution_timestamp,
                     execution_odds = excluded.execution_odds,
                     matched = excluded.matched,
                     closing_timestamp = excluded.closing_timestamp,
                     closing_odds = excluded.closing_odds,
                     settlement_timestamp = excluded.settlement_timestamp,
                     result = excluded.result,
                     stake = excluded.stake,
                     gross_pnl = excluded.gross_pnl,
                     commission = excluded.commission,
                     net_pnl = excluded.net_pnl,
                     decimal_clv_ratio = excluded.decimal_clv_ratio,
                     implied_probability_change = excluded.implied_probability_change,
                     paper_mode = excluded.paper_mode
                 """)) {
            bind(statement, trade);
            statement.executeUpdate();
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save paper trade.", exc);
        }
    }

    @Override
    public List<PaperTrade> listAll(String databasePath) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM paper_trades
                 ORDER BY recommendation_timestamp, id
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<PaperTrade> trades = new ArrayList<>();
            while (resultSet.next()) {
                trades.add(map(resultSet));
            }
            return trades;
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list paper trades.", exc);
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
                throw new IllegalStateException("Could not initialize paper trade schema.", exc);
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
                CREATE TABLE IF NOT EXISTS paper_trades (
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
                CREATE INDEX IF NOT EXISTS idx_paper_trades_status
                ON paper_trades(status, market_start_time)
                """);
        }
    }

    private void bind(PreparedStatement statement, PaperTrade trade) throws SQLException {
        statement.setString(1, trade.id());
        statement.setString(2, trade.exchange());
        statement.setString(3, trade.marketId());
        statement.setLong(4, trade.selectionId());
        statement.setString(5, trade.eventName());
        statement.setString(6, trade.marketName());
        statement.setString(7, trade.league());
        setInstant(statement, 8, trade.marketStartTime());
        statement.setString(9, trade.runnerName());
        statement.setString(10, trade.status().name());
        setInstant(statement, 11, trade.recommendationTimestamp());
        setDecimal(statement, 12, trade.availableBackOdds());
        setDecimal(statement, 13, trade.requestedOdds());
        setInstant(statement, 14, trade.executionTimestamp());
        setDecimal(statement, 15, trade.executionOdds());
        statement.setInt(16, trade.matched() ? 1 : 0);
        setInstant(statement, 17, trade.closingTimestamp());
        setDecimal(statement, 18, trade.closingOdds());
        setInstant(statement, 19, trade.settlementTimestamp());
        statement.setString(20, trade.result() == null ? null : trade.result().name());
        setDecimal(statement, 21, trade.stake());
        setDecimal(statement, 22, trade.grossPnl());
        setDecimal(statement, 23, trade.commission());
        setDecimal(statement, 24, trade.netPnl());
        setDecimal(statement, 25, trade.decimalClvRatio());
        setDecimal(statement, 26, trade.impliedProbabilityChange());
        statement.setInt(27, trade.paperMode() ? 1 : 0);
    }

    private PaperTrade map(ResultSet resultSet) throws SQLException {
        return new PaperTrade(
            resultSet.getString("id"),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getLong("selection_id"),
            resultSet.getString("event_name"),
            resultSet.getString("market_name"),
            resultSet.getString("league"),
            instant(resultSet, "market_start_time"),
            resultSet.getString("runner_name"),
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
            resultSet.getInt("paper_mode") == 1
        );
    }

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setString(index, value == null ? null : value.toString());
    }

    private Instant instant(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null ? null : Instant.parse(value);
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        statement.setString(index, value == null ? null : value.toPlainString());
    }

    private BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null ? null : new BigDecimal(value);
    }

    private BacktestOutcome outcome(String value) {
        return value == null || value.isBlank() ? null : BacktestOutcome.valueOf(value);
    }
}
