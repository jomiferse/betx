package com.betx.adapter.persistence;

import com.betx.application.PaperSignalEvaluation;
import com.betx.application.PaperTradeAnalyzerRejectionReason;
import com.betx.application.port.out.PaperSignalEvaluationRepository;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerType;
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
import java.util.Set;
import org.springframework.stereotype.Component;

/** SQLite repository for paper-trading analyzer evaluations, including rejections. */
@Component
public class JdbcPaperSignalEvaluationRepository implements PaperSignalEvaluationRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcPaperSignalEvaluationRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcPaperSignalEvaluationRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public void save(String databasePath, PaperSignalEvaluation evaluation) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO paper_signal_evaluations (
                     observed_at, exchange, market_id, market_name, event_name, competition_name, market_start_time,
                     selection_id, runner_name, runner_type, recommendation, score, confidence_label, reason,
                     best_back_price, best_lay_price, spread, liquidity,
                     back_percentage_delta, lay_percentage_delta, liquidity_percentage_delta, analyzer_reason
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            bind(statement, evaluation);
            statement.executeUpdate();
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not save paper signal evaluation.", exc);
        }
    }

    @Override
    public List<PaperSignalEvaluation> listLatest(String databasePath, int limit) {
        ensureSchemaInitialized(databasePath);
        int effectiveLimit = limit <= 0 ? 100 : limit;
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM paper_signal_evaluations
                 ORDER BY observed_at DESC, id DESC
                 LIMIT ?
                 """)) {
            statement.setInt(1, effectiveLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PaperSignalEvaluation> evaluations = new ArrayList<>();
                while (resultSet.next()) {
                    evaluations.add(map(resultSet));
                }
                return evaluations;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list paper signal evaluations.", exc);
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
                throw new IllegalStateException("Could not initialize paper signal evaluation schema.", exc);
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
                CREATE TABLE IF NOT EXISTS paper_signal_evaluations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observed_at TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    market_name TEXT,
                    event_name TEXT,
                    competition_name TEXT,
                    market_start_time TEXT,
                    selection_id INTEGER NOT NULL,
                    runner_name TEXT,
                    runner_type TEXT NOT NULL,
                    recommendation TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    confidence_label TEXT,
                    reason TEXT,
                    best_back_price TEXT,
                    best_lay_price TEXT,
                    spread TEXT,
                    liquidity TEXT,
                    back_percentage_delta TEXT,
                    lay_percentage_delta TEXT,
                    liquidity_percentage_delta TEXT,
                    analyzer_reason TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_paper_signal_evaluations_observed
                ON paper_signal_evaluations(observed_at DESC)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_paper_signal_evaluations_reason
                ON paper_signal_evaluations(analyzer_reason, competition_name, runner_type)
                """);
        }
    }

    private void bind(PreparedStatement statement, PaperSignalEvaluation evaluation) throws SQLException {
        setInstant(statement, 1, evaluation.observedAt());
        statement.setString(2, evaluation.exchange());
        statement.setString(3, evaluation.marketId());
        statement.setString(4, evaluation.marketName());
        statement.setString(5, evaluation.eventName());
        statement.setString(6, evaluation.competitionName());
        setInstant(statement, 7, evaluation.marketStartTime());
        statement.setLong(8, evaluation.selectionId());
        statement.setString(9, evaluation.runnerName());
        statement.setString(10, evaluation.runnerType().name());
        statement.setString(11, evaluation.recommendation().name());
        statement.setInt(12, evaluation.score());
        statement.setString(13, evaluation.confidenceLabel());
        statement.setString(14, evaluation.reason());
        setDecimal(statement, 15, evaluation.bestBackPrice());
        setDecimal(statement, 16, evaluation.bestLayPrice());
        setDecimal(statement, 17, evaluation.spread());
        setDecimal(statement, 18, evaluation.liquidity());
        setDecimal(statement, 19, evaluation.backPercentageDelta());
        setDecimal(statement, 20, evaluation.layPercentageDelta());
        setDecimal(statement, 21, evaluation.liquidityPercentageDelta());
        statement.setString(22, evaluation.analyzerReason().name());
    }

    private PaperSignalEvaluation map(ResultSet resultSet) throws SQLException {
        return new PaperSignalEvaluation(
            instant(resultSet, "observed_at"),
            resultSet.getString("exchange"),
            resultSet.getString("market_id"),
            resultSet.getString("market_name"),
            resultSet.getString("event_name"),
            resultSet.getString("competition_name"),
            instant(resultSet, "market_start_time"),
            resultSet.getLong("selection_id"),
            resultSet.getString("runner_name"),
            RunnerType.valueOf(resultSet.getString("runner_type")),
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
            PaperTradeAnalyzerRejectionReason.valueOf(resultSet.getString("analyzer_reason"))
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
}
