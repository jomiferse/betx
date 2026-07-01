package com.betx.adapter.persistence;

import com.betx.application.StakeSizingShadowDecision;
import com.betx.application.StakeSizingShadowDecisionUpsertAction;
import com.betx.application.StakeSizingShadowDecisionUpsertResult;
import com.betx.application.port.out.StakeSizingShadowDecisionRepository;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.staking.StakeSizingBlockReason;
import com.betx.domain.staking.StakeSizingDecisionReason;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
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
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JdbcStakeSizingShadowDecisionRepository implements StakeSizingShadowDecisionRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    private final String databasePath;
    private final Set<String> initializedDatabases = Collections.synchronizedSet(new HashSet<>());

    public JdbcStakeSizingShadowDecisionRepository() {
        this(DEFAULT_DATABASE_PATH);
    }

    public JdbcStakeSizingShadowDecisionRepository(String databasePath) {
        this.databasePath = databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath;
    }

    @Override
    public StakeSizingShadowDecisionUpsertResult upsert(String databasePath, StakeSizingShadowDecision decision) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path)) {
            beginImmediate(connection);
            try {
                StakeSizingShadowDecision existing = findExisting(connection, decision);
                StakeSizingShadowDecisionUpsertAction action;
                if (existing == null) {
                    insert(connection, decision);
                    action = StakeSizingShadowDecisionUpsertAction.CREATED;
                } else {
                    action = action(existing, decision);
                    updateExisting(connection, decision);
                }
                StakeSizingShadowDecision saved = findExisting(connection, decision);
                commit(connection);
                return new StakeSizingShadowDecisionUpsertResult(saved, action);
            } catch (SQLException | RuntimeException exc) {
                rollback(connection);
                throw exc;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not upsert stake sizing shadow decision.", exc);
        }
    }

    @Override
    public List<StakeSizingShadowDecision> list(String databasePath, Instant from, Instant to) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT *
                 FROM stake_sizing_shadow_decisions
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
                List<StakeSizingShadowDecision> decisions = new ArrayList<>();
                while (resultSet.next()) {
                    decisions.add(map(resultSet));
                }
                return decisions;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not list stake sizing shadow decisions.", exc);
        }
    }

    @Override
    public long countDuplicateLogicalKeys(String databasePath) {
        ensureSchemaInitialized(databasePath);
        String path = resolvedDatabasePath(databasePath);
        try (Connection connection = connection(path);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT COUNT(*)
                 FROM (
                     SELECT recommendation_id, policy_name, risk_profile, source, COUNT(*) AS rows_count
                     FROM stake_sizing_shadow_decisions
                     GROUP BY recommendation_id, policy_name, risk_profile, source
                     HAVING rows_count > 1
                 )
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not count duplicate stake sizing shadow decisions.", exc);
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
                throw new IllegalStateException("Could not initialize stake sizing shadow schema.", exc);
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
                CREATE TABLE IF NOT EXISTS stake_sizing_shadow_decisions (
                    id TEXT PRIMARY KEY,
                    recommendation_id TEXT NOT NULL,
                    canonical_key TEXT,
                    policy_name TEXT NOT NULL,
                    risk_profile TEXT NOT NULL,
                    source TEXT NOT NULL,
                    selection_side TEXT NOT NULL DEFAULT 'UNKNOWN',
                    odds TEXT,
                    strategy_name TEXT,
                    base_stake TEXT,
                    min_stake TEXT,
                    max_stake TEXT,
                    bankroll TEXT,
                    calculated_stake TEXT,
                    final_stake TEXT,
                    would_block INTEGER NOT NULL DEFAULT 0,
                    block_reason TEXT,
                    decision_reason TEXT NOT NULL,
                    adjustment_summary TEXT,
                    evaluated_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    last_evaluated_at TEXT NOT NULL,
                    observed_count INTEGER NOT NULL DEFAULT 1,
                    UNIQUE(recommendation_id, policy_name, risk_profile, source)
                )
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_stake_sizing_shadow_recommendation
                ON stake_sizing_shadow_decisions(recommendation_id)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_stake_sizing_shadow_policy_profile
                ON stake_sizing_shadow_decisions(policy_name, risk_profile)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_stake_sizing_shadow_last_evaluated_at
                ON stake_sizing_shadow_decisions(last_evaluated_at)
                """);
        }
    }

    private void insert(Connection connection, StakeSizingShadowDecision decision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO stake_sizing_shadow_decisions (
                id, recommendation_id, canonical_key, policy_name, risk_profile, source,
                selection_side, odds, strategy_name, base_stake, min_stake, max_stake,
                bankroll, calculated_stake, final_stake, would_block, block_reason,
                decision_reason, adjustment_summary, evaluated_at, created_at,
                last_evaluated_at, observed_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            bind(statement, decision);
            statement.executeUpdate();
        }
    }

    private void updateExisting(Connection connection, StakeSizingShadowDecision decision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE stake_sizing_shadow_decisions
            SET canonical_key = ?,
                selection_side = ?,
                odds = ?,
                strategy_name = ?,
                base_stake = ?,
                min_stake = ?,
                max_stake = ?,
                bankroll = ?,
                calculated_stake = ?,
                final_stake = ?,
                would_block = ?,
                block_reason = ?,
                decision_reason = ?,
                adjustment_summary = ?,
                last_evaluated_at = ?,
                observed_count = observed_count + 1
            WHERE recommendation_id = ?
              AND policy_name = ?
              AND risk_profile = ?
              AND source = ?
            """)) {
            statement.setString(1, decision.canonicalKey());
            statement.setString(2, decision.selectionSide().name());
            setDecimal(statement, 3, decision.odds());
            statement.setString(4, decision.strategyName());
            setDecimal(statement, 5, decision.baseStake());
            setDecimal(statement, 6, decision.minStake());
            setDecimal(statement, 7, decision.maxStake());
            setDecimal(statement, 8, decision.bankroll());
            setDecimal(statement, 9, decision.calculatedStake());
            setDecimal(statement, 10, decision.finalStake());
            statement.setInt(11, decision.wouldBlock() ? 1 : 0);
            statement.setString(12, decision.blockReason() == null ? null : decision.blockReason().name());
            statement.setString(13, decision.decisionReason().name());
            statement.setString(14, decision.adjustmentSummary());
            statement.setString(15, instant(decision.lastEvaluatedAt()));
            statement.setString(16, decision.recommendationId());
            statement.setString(17, decision.policyName().name());
            statement.setString(18, decision.riskProfile().name());
            statement.setString(19, decision.source().name());
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, StakeSizingShadowDecision decision) throws SQLException {
        statement.setString(1, decision.id());
        statement.setString(2, decision.recommendationId());
        statement.setString(3, decision.canonicalKey());
        statement.setString(4, decision.policyName().name());
        statement.setString(5, decision.riskProfile().name());
        statement.setString(6, decision.source().name());
        statement.setString(7, decision.selectionSide().name());
        setDecimal(statement, 8, decision.odds());
        statement.setString(9, decision.strategyName());
        setDecimal(statement, 10, decision.baseStake());
        setDecimal(statement, 11, decision.minStake());
        setDecimal(statement, 12, decision.maxStake());
        setDecimal(statement, 13, decision.bankroll());
        setDecimal(statement, 14, decision.calculatedStake());
        setDecimal(statement, 15, decision.finalStake());
        statement.setInt(16, decision.wouldBlock() ? 1 : 0);
        statement.setString(17, decision.blockReason() == null ? null : decision.blockReason().name());
        statement.setString(18, decision.decisionReason().name());
        statement.setString(19, decision.adjustmentSummary());
        statement.setString(20, instant(decision.evaluatedAt()));
        statement.setString(21, instant(decision.createdAt()));
        statement.setString(22, instant(decision.lastEvaluatedAt()));
        statement.setLong(23, decision.observedCount());
    }

    private StakeSizingShadowDecision findExisting(Connection connection, StakeSizingShadowDecision decision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM stake_sizing_shadow_decisions
            WHERE recommendation_id = ?
              AND policy_name = ?
              AND risk_profile = ?
              AND source = ?
            """)) {
            statement.setString(1, decision.recommendationId());
            statement.setString(2, decision.policyName().name());
            statement.setString(3, decision.riskProfile().name());
            statement.setString(4, decision.source().name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    private StakeSizingShadowDecisionUpsertAction action(
        StakeSizingShadowDecision existing,
        StakeSizingShadowDecision decision
    ) {
        if (existing.wouldBlock() != decision.wouldBlock()
            || !Objects.equals(existing.blockReason(), decision.blockReason())) {
            return StakeSizingShadowDecisionUpsertAction.UPDATED_DECISION_CHANGED;
        }
        if (compare(existing.finalStake(), decision.finalStake()) != 0
            || compare(existing.calculatedStake(), decision.calculatedStake()) != 0) {
            return StakeSizingShadowDecisionUpsertAction.UPDATED_STAKE_CHANGED;
        }
        if (existing.decisionReason() != decision.decisionReason()) {
            return StakeSizingShadowDecisionUpsertAction.UPDATED_REASON_CHANGED;
        }
        return StakeSizingShadowDecisionUpsertAction.OBSERVED_UNCHANGED;
    }

    private StakeSizingShadowDecision map(ResultSet resultSet) throws SQLException {
        return new StakeSizingShadowDecision(
            resultSet.getString("id"),
            resultSet.getString("recommendation_id"),
            resultSet.getString("canonical_key"),
            StakeSizingMode.valueOf(resultSet.getString("policy_name")),
            StakeSizingRiskProfile.valueOf(resultSet.getString("risk_profile")),
            StakeSizingSource.valueOf(resultSet.getString("source")),
            selectionSide(resultSet.getString("selection_side")),
            decimal(resultSet.getString("odds")),
            resultSet.getString("strategy_name"),
            decimal(resultSet.getString("base_stake")),
            decimal(resultSet.getString("min_stake")),
            decimal(resultSet.getString("max_stake")),
            decimal(resultSet.getString("bankroll")),
            decimal(resultSet.getString("calculated_stake")),
            decimal(resultSet.getString("final_stake")),
            resultSet.getInt("would_block") == 1,
            blockReason(resultSet.getString("block_reason")),
            StakeSizingDecisionReason.valueOf(resultSet.getString("decision_reason")),
            resultSet.getString("adjustment_summary"),
            Instant.parse(resultSet.getString("evaluated_at")),
            Instant.parse(resultSet.getString("created_at")),
            Instant.parse(resultSet.getString("last_evaluated_at")),
            resultSet.getLong("observed_count")
        );
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

    private static int compare(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        statement.setString(index, value == null ? null : value.toPlainString());
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static SelectionSide selectionSide(String value) {
        return value == null || value.isBlank() ? SelectionSide.UNKNOWN : SelectionSide.valueOf(value);
    }

    private static StakeSizingBlockReason blockReason(String value) {
        return value == null || value.isBlank() ? null : StakeSizingBlockReason.valueOf(value);
    }
}
