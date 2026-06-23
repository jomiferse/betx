package com.betx.adapter.persistence;

import com.betx.application.BetRecommendation;
import com.betx.application.BetRecommendationSource;
import com.betx.application.BetRecommendationStatus;
import com.betx.application.BetRecommendationUpsertAction;
import com.betx.application.BetRecommendationUpsertResult;
import com.betx.application.port.out.BetRecommendationRepository;
import com.betx.domain.order.SelectionSide;
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

@Component
public class JdbcBetRecommendationRepository implements BetRecommendationRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcBetRecommendationRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcBetRecommendationRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public void save(String databasePath, BetRecommendation recommendation) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO bet_recommendations (
                     id, evaluation_id, exchange, market_id, selection_id, selection_side,
                     event_name, runner_name, competition_name, market_start_time, strategy_name,
                     recommended_odds, observed_at, recommended_at, source, status, created_at,
                     confidence, edge, liquidity, reason, canonical_key, first_seen_at, last_seen_at,
                     observed_count, initial_recommended_odds, latest_recommended_odds,
                     best_recommended_odds, covered_at, expired_at, last_evaluation_id
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            bind(statement, recommendation);
            statement.executeUpdate();
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save bet recommendation.", exc);
        }
    }

    @Override
    public BetRecommendationUpsertResult upsertActiveRecommendation(String databasePath, BetRecommendation recommendation) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path)) {
            beginImmediate(connection);
            try {
                Optional<BetRecommendation> existing = findCanonicalForUpdate(connection, recommendation.canonicalKey());
                BetRecommendationUpsertResult result;
                if (existing.isEmpty()) {
                    insert(connection, recommendation);
                    result = new BetRecommendationUpsertResult(recommendation, BetRecommendationUpsertAction.CREATED);
                } else {
                    BetRecommendation updated = existing.get().observedAgain(
                        recommendation.evaluationId(),
                        recommendation.recommendedOdds(),
                        recommendation.observedAt()
                    );
                    updateCanonicalFields(connection, updated);
                    BetRecommendationUpsertAction action = updated.status() == BetRecommendationStatus.COVERED
                        ? BetRecommendationUpsertAction.COVERED
                        : BetRecommendationUpsertAction.OBSERVED;
                    result = new BetRecommendationUpsertResult(updated, action);
                }
                commit(connection);
                return result;
            } catch (RuntimeException | SQLException exc) {
                rollback(connection);
                throw exc;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not upsert bet recommendation.", exc);
        }
    }

    @Override
    public Optional<BetRecommendationUpsertResult> markCovered(String databasePath, String canonicalKey, Instant coveredAt) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path)) {
            beginImmediate(connection);
            try {
                Optional<BetRecommendation> existing = findCanonicalForUpdate(connection, canonicalKey);
                Optional<BetRecommendationUpsertResult> result = Optional.empty();
                if (existing.isPresent()) {
                    BetRecommendation current = existing.get();
                    boolean transitioned = current.status() == BetRecommendationStatus.ACTIVE;
                    BetRecommendation covered = current.covered(coveredAt);
                    updateCanonicalFields(connection, covered);
                    result = Optional.of(new BetRecommendationUpsertResult(
                        covered,
                        transitioned ? BetRecommendationUpsertAction.COVERED : BetRecommendationUpsertAction.OBSERVED
                    ));
                }
                commit(connection);
                return result;
            } catch (RuntimeException | SQLException exc) {
                rollback(connection);
                throw exc;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not mark bet recommendation covered.", exc);
        }
    }

    @Override
    public Optional<BetRecommendation> findById(String databasePath, String id) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM bet_recommendations
                 WHERE id = ?
                 """)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read bet recommendation.", exc);
        }
    }

    @Override
    public List<BetRecommendation> findByEvaluationId(String databasePath, String evaluationId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM bet_recommendations
                 WHERE evaluation_id = ?
                 ORDER BY recommended_at ASC, id ASC
                 """)) {
            statement.setString(1, evaluationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BetRecommendation> recommendations = new ArrayList<>();
                while (resultSet.next()) {
                    recommendations.add(map(resultSet));
                }
                return recommendations;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read bet recommendations by evaluation id.", exc);
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
                throw new IllegalStateException("Could not initialize bet recommendation schema.", exc);
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
                CREATE TABLE IF NOT EXISTS bet_recommendations (
                    id TEXT PRIMARY KEY,
                    evaluation_id TEXT,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    selection_side TEXT NOT NULL DEFAULT 'UNKNOWN',
                    event_name TEXT,
                    runner_name TEXT,
                    competition_name TEXT,
                    market_start_time TEXT,
                    strategy_name TEXT,
                    recommended_odds TEXT,
                    observed_at TEXT NOT NULL,
                    recommended_at TEXT NOT NULL,
                    source TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    confidence INTEGER,
                    edge TEXT,
                    liquidity TEXT,
                    reason TEXT,
                    canonical_key TEXT,
                    first_seen_at TEXT,
                    last_seen_at TEXT,
                    observed_count INTEGER,
                    initial_recommended_odds TEXT,
                    latest_recommended_odds TEXT,
                    best_recommended_odds TEXT,
                    covered_at TEXT,
                    expired_at TEXT,
                    last_evaluation_id TEXT
                )
                """);
            addColumnIfMissing(connection, "canonical_key", "TEXT");
            addColumnIfMissing(connection, "first_seen_at", "TEXT");
            addColumnIfMissing(connection, "last_seen_at", "TEXT");
            addColumnIfMissing(connection, "observed_count", "INTEGER");
            addColumnIfMissing(connection, "initial_recommended_odds", "TEXT");
            addColumnIfMissing(connection, "latest_recommended_odds", "TEXT");
            addColumnIfMissing(connection, "best_recommended_odds", "TEXT");
            addColumnIfMissing(connection, "covered_at", "TEXT");
            addColumnIfMissing(connection, "expired_at", "TEXT");
            addColumnIfMissing(connection, "last_evaluation_id", "TEXT");
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_evaluation_id
                ON bet_recommendations(evaluation_id)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_canonical_key
                ON bet_recommendations(canonical_key)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_canonical_status
                ON bet_recommendations(exchange, market_id, selection_id, selection_side, strategy_name, status)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_last_seen_at
                ON bet_recommendations(last_seen_at)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_match_key
                ON bet_recommendations(exchange, market_id, selection_id, selection_side)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_recommended_at
                ON bet_recommendations(recommended_at)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_strategy_name
                ON bet_recommendations(strategy_name)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_recommendations_status
                ON bet_recommendations(status)
                """);
        }
    }

    private void bind(PreparedStatement statement, BetRecommendation recommendation) throws SQLException {
        statement.setString(1, recommendation.id());
        statement.setString(2, recommendation.evaluationId());
        statement.setString(3, recommendation.exchange());
        statement.setString(4, recommendation.marketId());
        statement.setLong(5, recommendation.selectionId());
        statement.setString(6, recommendation.selectionSide().name());
        statement.setString(7, recommendation.eventName());
        statement.setString(8, recommendation.runnerName());
        statement.setString(9, recommendation.competitionName());
        setInstant(statement, 10, recommendation.marketStartTime());
        statement.setString(11, recommendation.strategyName());
        setDecimal(statement, 12, recommendation.recommendedOdds());
        setInstant(statement, 13, recommendation.observedAt());
        setInstant(statement, 14, recommendation.recommendedAt());
        statement.setString(15, recommendation.source().name());
        statement.setString(16, recommendation.status().name());
        setInstant(statement, 17, recommendation.createdAt());
        if (recommendation.confidence() == null) {
            statement.setObject(18, null);
        } else {
            statement.setInt(18, recommendation.confidence());
        }
        setDecimal(statement, 19, recommendation.edge());
        setDecimal(statement, 20, recommendation.liquidity());
        statement.setString(21, recommendation.reason());
        statement.setString(22, recommendation.canonicalKey());
        setInstant(statement, 23, recommendation.firstSeenAt());
        setInstant(statement, 24, recommendation.lastSeenAt());
        statement.setLong(25, recommendation.observedCount());
        setDecimal(statement, 26, recommendation.initialRecommendedOdds());
        setDecimal(statement, 27, recommendation.latestRecommendedOdds());
        setDecimal(statement, 28, recommendation.bestRecommendedOdds());
        setInstant(statement, 29, recommendation.coveredAt());
        setInstant(statement, 30, recommendation.expiredAt());
        statement.setString(31, recommendation.lastEvaluationId());
    }

    private BetRecommendation map(ResultSet resultSet) throws SQLException {
        return new BetRecommendation(
            resultSet.getString("id"),
            resultSet.getString("evaluation_id"),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getLong("selection_id"),
            selectionSide(resultSet.getString("selection_side")),
            resultSet.getString("event_name"),
            resultSet.getString("runner_name"),
            resultSet.getString("competition_name"),
            instant(resultSet, "market_start_time"),
            resultSet.getString("strategy_name"),
            decimal(resultSet, "recommended_odds"),
            Instant.parse(resultSet.getString("observed_at")),
            Instant.parse(resultSet.getString("recommended_at")),
            BetRecommendationSource.valueOf(resultSet.getString("source")),
            BetRecommendationStatus.valueOf(resultSet.getString("status")),
            Instant.parse(resultSet.getString("created_at")),
            integer(resultSet, "confidence"),
            decimal(resultSet, "edge"),
            decimal(resultSet, "liquidity"),
            resultSet.getString("reason"),
            resultSet.getString("canonical_key"),
            instant(resultSet, "first_seen_at"),
            instant(resultSet, "last_seen_at"),
            longValue(resultSet, "observed_count", 1),
            decimal(resultSet, "initial_recommended_odds"),
            decimal(resultSet, "latest_recommended_odds"),
            decimal(resultSet, "best_recommended_odds"),
            instant(resultSet, "covered_at"),
            instant(resultSet, "expired_at"),
            resultSet.getString("last_evaluation_id")
        );
    }

    private void insert(Connection connection, BetRecommendation recommendation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO bet_recommendations (
                id, evaluation_id, exchange, market_id, selection_id, selection_side,
                event_name, runner_name, competition_name, market_start_time, strategy_name,
                recommended_odds, observed_at, recommended_at, source, status, created_at,
                confidence, edge, liquidity, reason, canonical_key, first_seen_at, last_seen_at,
                observed_count, initial_recommended_odds, latest_recommended_odds,
                best_recommended_odds, covered_at, expired_at, last_evaluation_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            bind(statement, recommendation);
            statement.executeUpdate();
        }
    }

    private Optional<BetRecommendation> findCanonicalForUpdate(Connection connection, String canonicalKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM bet_recommendations
            WHERE canonical_key = ?
              AND status IN ('ACTIVE', 'COVERED')
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """)) {
            statement.setString(1, canonicalKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    private void updateCanonicalFields(Connection connection, BetRecommendation recommendation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE bet_recommendations
            SET status = ?,
                last_seen_at = ?,
                observed_count = ?,
                latest_recommended_odds = ?,
                best_recommended_odds = ?,
                covered_at = ?,
                expired_at = ?,
                last_evaluation_id = ?
            WHERE id = ?
            """)) {
            statement.setString(1, recommendation.status().name());
            setInstant(statement, 2, recommendation.lastSeenAt());
            statement.setLong(3, recommendation.observedCount());
            setDecimal(statement, 4, recommendation.latestRecommendedOdds());
            setDecimal(statement, 5, recommendation.bestRecommendedOdds());
            setInstant(statement, 6, recommendation.coveredAt());
            setInstant(statement, 7, recommendation.expiredAt());
            statement.setString(8, recommendation.lastEvaluationId());
            statement.setString(9, recommendation.id());
            statement.executeUpdate();
        }
    }

    private void beginImmediate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("BEGIN IMMEDIATE");
        }
    }

    private void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("COMMIT");
        }
    }

    private void rollback(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ROLLBACK");
        } catch (SQLException ignored) {
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition) throws SQLException {
        if (columnExists(connection, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE bet_recommendations ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("PRAGMA table_info(bet_recommendations)")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private SelectionSide selectionSide(String value) {
        if (value == null || value.isBlank()) {
            return SelectionSide.UNKNOWN;
        }
        return SelectionSide.valueOf(value);
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        statement.setString(index, value == null ? null : value.toPlainString());
    }

    private BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setString(index, value == null ? null : value.toString());
    }

    private Instant instant(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private Integer integer(ResultSet resultSet, String field) throws SQLException {
        int value = resultSet.getInt(field);
        return resultSet.wasNull() ? null : value;
    }

    private long longValue(ResultSet resultSet, String field, long defaultValue) throws SQLException {
        long value = resultSet.getLong(field);
        return resultSet.wasNull() ? defaultValue : value;
    }
}
