package com.betx.adapter.persistence;

import com.betx.application.CandidateFilterDecision;
import com.betx.application.CandidateFilterDecisionReason;
import com.betx.application.CandidateFilterEvaluation;
import com.betx.application.CandidateFilterEvaluationUpsertAction;
import com.betx.application.CandidateFilterEvaluationUpsertResult;
import com.betx.application.CandidateFilterName;
import com.betx.application.CandidateFilterSource;
import com.betx.application.port.out.CandidateFilterEvaluationRepository;
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
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JdbcCandidateFilterEvaluationRepository implements CandidateFilterEvaluationRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcCandidateFilterEvaluationRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcCandidateFilterEvaluationRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public CandidateFilterEvaluationUpsertResult upsert(String databasePath, CandidateFilterEvaluation evaluation) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("BEGIN IMMEDIATE");
            }
            try {
                CandidateFilterEvaluation existing = findExisting(connection, evaluation);
                CandidateFilterEvaluationUpsertAction action;
                if (existing == null) {
                    insert(connection, evaluation);
                    action = CandidateFilterEvaluationUpsertAction.CREATED;
                } else {
                    action = action(existing, evaluation);
                    updateExisting(connection, evaluation);
                }
                CandidateFilterEvaluation saved = findExisting(connection, evaluation);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("COMMIT");
                }
                return new CandidateFilterEvaluationUpsertResult(saved, action);
            } catch (SQLException | RuntimeException exc) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ROLLBACK");
                }
                throw exc;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not upsert candidate filter evaluation.", exc);
        }
    }

    @Override
    public List<CandidateFilterEvaluation> list(String databasePath, Instant from, Instant to) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM candidate_filter_evaluations
                 WHERE (? IS NULL OR last_evaluated_at >= ?)
                   AND (? IS NULL OR last_evaluated_at <= ?)
                 ORDER BY last_evaluated_at ASC, id ASC
                 """)) {
            String fromText = instant(from);
            String toText = instant(to);
            statement.setString(1, fromText);
            statement.setString(2, fromText);
            statement.setString(3, toText);
            statement.setString(4, toText);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CandidateFilterEvaluation> evaluations = new ArrayList<>();
                while (resultSet.next()) {
                    evaluations.add(map(resultSet));
                }
                return evaluations;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list candidate filter evaluations.", exc);
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
                throw new IllegalStateException("Could not initialize candidate filter evaluation schema.", exc);
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
                CREATE TABLE IF NOT EXISTS candidate_filter_evaluations (
                    id TEXT PRIMARY KEY,
                    recommendation_id TEXT NOT NULL,
                    canonical_key TEXT,
                    filter_name TEXT NOT NULL,
                    decision TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    selection_side TEXT NOT NULL DEFAULT 'UNKNOWN',
                    odds TEXT,
                    strategy_name TEXT,
                    source TEXT NOT NULL,
                    evaluated_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    last_evaluated_at TEXT NOT NULL,
                    observed_count INTEGER NOT NULL DEFAULT 1,
                    UNIQUE(recommendation_id, filter_name, source)
                )
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_candidate_filter_evaluations_recommendation
                ON candidate_filter_evaluations(recommendation_id)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_candidate_filter_evaluations_filter_source
                ON candidate_filter_evaluations(filter_name, source)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_candidate_filter_evaluations_last_evaluated_at
                ON candidate_filter_evaluations(last_evaluated_at)
                """);
        }
    }

    private void bind(PreparedStatement statement, CandidateFilterEvaluation evaluation) throws SQLException {
        statement.setString(1, evaluation.id());
        statement.setString(2, evaluation.recommendationId());
        statement.setString(3, evaluation.canonicalKey());
        statement.setString(4, evaluation.filterName().name());
        statement.setString(5, evaluation.decision().name());
        statement.setString(6, evaluation.reason().name());
        statement.setString(7, evaluation.selectionSide().name());
        statement.setString(8, evaluation.odds() == null ? null : evaluation.odds().toPlainString());
        statement.setString(9, evaluation.strategyName());
        statement.setString(10, evaluation.source().name());
        statement.setString(11, instant(evaluation.evaluatedAt()));
        statement.setString(12, instant(evaluation.createdAt()));
        statement.setString(13, instant(evaluation.lastEvaluatedAt()));
        statement.setLong(14, evaluation.observedCount());
    }

    private void insert(Connection connection, CandidateFilterEvaluation evaluation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO candidate_filter_evaluations (
                id, recommendation_id, canonical_key, filter_name, decision, reason,
                selection_side, odds, strategy_name, source, evaluated_at, created_at,
                last_evaluated_at, observed_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            bind(statement, evaluation);
            statement.executeUpdate();
        }
    }

    private void updateExisting(Connection connection, CandidateFilterEvaluation evaluation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE candidate_filter_evaluations
            SET decision = ?,
                reason = ?,
                selection_side = ?,
                odds = ?,
                strategy_name = ?,
                last_evaluated_at = ?,
                observed_count = observed_count + 1
            WHERE recommendation_id = ?
              AND filter_name = ?
              AND source = ?
            """)) {
            statement.setString(1, evaluation.decision().name());
            statement.setString(2, evaluation.reason().name());
            statement.setString(3, evaluation.selectionSide().name());
            statement.setString(4, evaluation.odds() == null ? null : evaluation.odds().toPlainString());
            statement.setString(5, evaluation.strategyName());
            statement.setString(6, instant(evaluation.lastEvaluatedAt()));
            statement.setString(7, evaluation.recommendationId());
            statement.setString(8, evaluation.filterName().name());
            statement.setString(9, evaluation.source().name());
            statement.executeUpdate();
        }
    }

    private CandidateFilterEvaluation findExisting(Connection connection, CandidateFilterEvaluation evaluation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM candidate_filter_evaluations
            WHERE recommendation_id = ?
              AND filter_name = ?
              AND source = ?
            """)) {
            statement.setString(1, evaluation.recommendationId());
            statement.setString(2, evaluation.filterName().name());
            statement.setString(3, evaluation.source().name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return map(resultSet);
                }
                return null;
            }
        }
    }

    private CandidateFilterEvaluationUpsertAction action(
        CandidateFilterEvaluation existing,
        CandidateFilterEvaluation evaluation
    ) {
        if (existing.decision() != evaluation.decision()) {
            return CandidateFilterEvaluationUpsertAction.UPDATED_DECISION_CHANGED;
        }
        if (existing.reason() != evaluation.reason()) {
            return CandidateFilterEvaluationUpsertAction.UPDATED_REASON_CHANGED;
        }
        return CandidateFilterEvaluationUpsertAction.OBSERVED_UNCHANGED;
    }

    private CandidateFilterEvaluation map(ResultSet resultSet) throws SQLException {
        return new CandidateFilterEvaluation(
            resultSet.getString("id"),
            resultSet.getString("recommendation_id"),
            resultSet.getString("canonical_key"),
            CandidateFilterName.valueOf(resultSet.getString("filter_name")),
            CandidateFilterDecision.valueOf(resultSet.getString("decision")),
            CandidateFilterDecisionReason.valueOf(resultSet.getString("reason")),
            selectionSide(resultSet.getString("selection_side")),
            decimal(resultSet.getString("odds")),
            resultSet.getString("strategy_name"),
            CandidateFilterSource.valueOf(resultSet.getString("source")),
            Instant.parse(resultSet.getString("evaluated_at")),
            Instant.parse(resultSet.getString("created_at")),
            Instant.parse(resultSet.getString("last_evaluated_at")),
            resultSet.getLong("observed_count")
        );
    }

    private static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static SelectionSide selectionSide(String value) {
        if (value == null || value.isBlank()) {
            return SelectionSide.UNKNOWN;
        }
        return SelectionSide.valueOf(value);
    }
}
