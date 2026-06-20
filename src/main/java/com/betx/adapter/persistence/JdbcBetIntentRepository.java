package com.betx.adapter.persistence;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.order.BetExecutionStatus;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.BetSide;
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
                WHERE exchange = ? AND market_id = ? AND selection_id = ?
                    AND stage IN ('AWAITING_CONFIRMATION', 'AWAITING_STAKE', 'EXECUTED')
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
    public Optional<BetIntent> findDuplicateBlockingByKey(
        String databasePath,
        String exchange,
        String marketId,
        long selectionId,
        BetSide side
    ) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND market_id = ? AND selection_id = ? AND side = ?
                    AND stage IN ('AWAITING_CONFIRMATION', 'AWAITING_STAKE', 'EXECUTED', 'SETTLED')
                ORDER BY created_at DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                statement.setLong(3, selectionId);
                statement.setString(4, side == null ? BetSide.BACK.name() : side.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read duplicate-blocking live bet intent.", exc);
        }
    }

    @Override
    public Optional<BetIntent> claimDuplicateProtectionKey(String databasePath, BetIntent intent) {
        ensureSchemaInitialized(databasePath);
        Optional<BetIntent> existing = findDuplicateBlockingByKey(
            databasePath,
            intent.exchange(),
            intent.marketId(),
            intent.selectionId(),
            intent.side()
        );
        if (existing.isPresent()) {
            return existing;
        }
        try (Connection connection = connection(databasePath)) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT OR IGNORE INTO bet_intent_deduplication_keys (
                    exchange, market_id, selection_id, side, bet_intent_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                insert.setString(1, intent.exchange());
                insert.setString(2, intent.marketId());
                insert.setLong(3, intent.selectionId());
                insert.setString(4, intent.side().name());
                insert.setString(5, intent.id());
                insert.setString(6, intent.createdAt().toString());
                int inserted = insert.executeUpdate();
                if (inserted > 0) {
                    connection.commit();
                    return Optional.empty();
                }
            }
            try (PreparedStatement select = connection.prepareStatement("""
                SELECT bet_intent_id
                FROM bet_intent_deduplication_keys
                WHERE exchange = ? AND market_id = ? AND selection_id = ? AND side = ?
                LIMIT 1
                """)) {
                select.setString(1, intent.exchange());
                select.setString(2, intent.marketId());
                select.setLong(3, intent.selectionId());
                select.setString(4, intent.side().name());
                try (ResultSet resultSet = select.executeQuery()) {
                    connection.commit();
                    if (resultSet.next()) {
                        String existingId = resultSet.getString("bet_intent_id");
                        return findById(databasePath, existingId).or(() -> Optional.of(intent.withId(existingId)));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not claim duplicate protection key.", exc);
        }
    }

    @Override
    public void releaseDuplicateProtectionKey(String databasePath, BetIntent intent) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath);
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM bet_intent_deduplication_keys
                 WHERE exchange = ? AND market_id = ? AND selection_id = ? AND side = ? AND bet_intent_id = ?
                 """)) {
            statement.setString(1, intent.exchange());
            statement.setString(2, intent.marketId());
            statement.setLong(3, intent.selectionId());
            statement.setString(4, intent.side().name());
            statement.setString(5, intent.id());
            statement.executeUpdate();
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not release duplicate protection key.", exc);
        }
    }

    @Override
    public Optional<BetIntent> findActiveByMarket(String databasePath, String exchange, String marketId) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND market_id = ?
                    AND stage IN ('AWAITING_CONFIRMATION', 'AWAITING_STAKE', 'EXECUTED')
                ORDER BY created_at DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read live bet intent for market.", exc);
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
    public Optional<BetIntent> findLatestByMarketSince(
        String databasePath,
        String exchange,
        String marketId,
        Instant since
    ) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND market_id = ? AND updated_at >= ?
                ORDER BY updated_at DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, marketId);
                statement.setString(3, since.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read recent live bet intent for market.", exc);
        }
    }

    @Override
    public Optional<BetIntent> findLatestByExchangeResultSince(
        String databasePath,
        String exchange,
        String resultMessage,
        Instant since
    ) {
        ensureSchemaInitialized(databasePath);
        try (Connection connection = connection(databasePath)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM bet_intents
                WHERE exchange = ? AND result_message = ? AND updated_at >= ?
                ORDER BY updated_at DESC
                LIMIT 1
                """)) {
                statement.setString(1, exchange);
                statement.setString(2, resultMessage);
                statement.setString(3, since.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(resultSet));
                }
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read recent exchange-level live bet intent.", exc);
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
                    competition_name = ?, selection_side = ?, strategy_name = ?, side = ?,
                    reason = ?, odds = ?, max_stake = ?, available_balance = ?, effective_available_balance = ?,
                    reserved_balance = ?, balance_snapshot_at = ?, selected_stake = ?, stage = ?,
                    result_message = ?, external_order_id = ?, settled_at = ?, settlement_result = ?, realized_profit_loss = ?,
                    created_at = ?, updated_at = ?, evaluation_id = ?, recommendation_id = ?, recommended_at = ?,
                    recommended_odds = ?, order_submitted_at = ?, order_response_at = ?, order_accepted_at = ?,
                    executed_at = ?, requested_odds = ?, average_executed_odds = ?, requested_stake = ?,
                    matched_stake = ?, remaining_stake = ?, execution_status = ?
                WHERE id = ?
                """ : """
                INSERT INTO bet_intents (
                    id, source, exchange, market_id, selection_id, event_name, market_name, runner_name,
                    competition_name, selection_side, strategy_name, side,
                    reason, odds, max_stake, available_balance, effective_available_balance, reserved_balance,
                    balance_snapshot_at, selected_stake, stage, result_message,
                    external_order_id, settled_at, settlement_result, realized_profit_loss, created_at, updated_at,
                    evaluation_id, recommendation_id, recommended_at, recommended_odds, order_submitted_at,
                    order_response_at, order_accepted_at, executed_at, requested_odds, average_executed_odds,
                    requested_stake, matched_stake, remaining_stake, execution_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        statement.setString(9, intent.competitionName());
        statement.setString(10, intent.selectionSide().name());
        statement.setString(11, intent.strategyName());
        statement.setString(12, intent.side().name());
        statement.setString(13, intent.reason());
        setDecimal(statement, 14, intent.odds());
        setDecimal(statement, 15, intent.maxStake());
        setDecimal(statement, 16, intent.availableBalance());
        setDecimal(statement, 17, intent.effectiveAvailableBalance());
        setDecimal(statement, 18, intent.reservedBalance());
        setInstant(statement, 19, intent.balanceSnapshotAt());
        setDecimal(statement, 20, intent.selectedStake());
        statement.setString(21, intent.stage().name());
        statement.setString(22, intent.resultMessage());
        statement.setString(23, intent.externalOrderId());
        setInstant(statement, 24, intent.settledAt());
        statement.setString(25, intent.settlementResult() == null ? null : intent.settlementResult().name());
        setDecimal(statement, 26, intent.realizedProfitLoss());
        statement.setString(27, intent.createdAt().toString());
        statement.setString(28, intent.updatedAt().toString());
        statement.setString(29, intent.evaluationId());
        statement.setString(30, intent.recommendationId());
        setInstant(statement, 31, intent.recommendedAt());
        setDecimal(statement, 32, intent.recommendedOdds());
        setInstant(statement, 33, intent.orderSubmittedAt());
        setInstant(statement, 34, intent.orderResponseAt());
        setInstant(statement, 35, intent.orderAcceptedAt());
        setInstant(statement, 36, intent.executedAt());
        setDecimal(statement, 37, intent.requestedOdds());
        setDecimal(statement, 38, intent.averageExecutedOdds());
        setDecimal(statement, 39, intent.requestedStake());
        setDecimal(statement, 40, intent.matchedStake());
        setDecimal(statement, 41, intent.remainingStake());
        statement.setString(42, intent.executionStatus() == null ? null : intent.executionStatus().name());
    }

    private void bindIntentUpdate(PreparedStatement statement, BetIntent intent) throws SQLException {
        statement.setString(1, intent.source().name());
        statement.setString(2, intent.exchange());
        statement.setString(3, intent.marketId());
        statement.setLong(4, intent.selectionId());
        statement.setString(5, intent.eventName());
        statement.setString(6, intent.marketName());
        statement.setString(7, intent.runnerName());
        statement.setString(8, intent.competitionName());
        statement.setString(9, intent.selectionSide().name());
        statement.setString(10, intent.strategyName());
        statement.setString(11, intent.side().name());
        statement.setString(12, intent.reason());
        setDecimal(statement, 13, intent.odds());
        setDecimal(statement, 14, intent.maxStake());
        setDecimal(statement, 15, intent.availableBalance());
        setDecimal(statement, 16, intent.effectiveAvailableBalance());
        setDecimal(statement, 17, intent.reservedBalance());
        setInstant(statement, 18, intent.balanceSnapshotAt());
        setDecimal(statement, 19, intent.selectedStake());
        statement.setString(20, intent.stage().name());
        statement.setString(21, intent.resultMessage());
        statement.setString(22, intent.externalOrderId());
        setInstant(statement, 23, intent.settledAt());
        statement.setString(24, intent.settlementResult() == null ? null : intent.settlementResult().name());
        setDecimal(statement, 25, intent.realizedProfitLoss());
        statement.setString(26, intent.createdAt().toString());
        statement.setString(27, intent.updatedAt().toString());
        statement.setString(28, intent.evaluationId());
        statement.setString(29, intent.recommendationId());
        setInstant(statement, 30, intent.recommendedAt());
        setDecimal(statement, 31, intent.recommendedOdds());
        setInstant(statement, 32, intent.orderSubmittedAt());
        setInstant(statement, 33, intent.orderResponseAt());
        setInstant(statement, 34, intent.orderAcceptedAt());
        setInstant(statement, 35, intent.executedAt());
        setDecimal(statement, 36, intent.requestedOdds());
        setDecimal(statement, 37, intent.averageExecutedOdds());
        setDecimal(statement, 38, intent.requestedStake());
        setDecimal(statement, 39, intent.matchedStake());
        setDecimal(statement, 40, intent.remainingStake());
        statement.setString(41, intent.executionStatus() == null ? null : intent.executionStatus().name());
        statement.setString(42, intent.id());
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
                    competition_name TEXT,
                    selection_side TEXT NOT NULL DEFAULT 'UNKNOWN',
                    strategy_name TEXT,
                    side TEXT NOT NULL DEFAULT 'BACK',
                    reason TEXT,
                    odds TEXT NOT NULL,
                    max_stake TEXT NOT NULL,
                    available_balance TEXT,
                    effective_available_balance TEXT,
                    reserved_balance TEXT,
                    balance_snapshot_at TEXT,
                    selected_stake TEXT,
                    stage TEXT NOT NULL,
                    result_message TEXT,
                    external_order_id TEXT,
                    settled_at TEXT,
                    settlement_result TEXT,
                    realized_profit_loss TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            addColumnIfMissing(connection, "bet_intents", "source", "TEXT NOT NULL DEFAULT 'TELEGRAM_CONFIRMATION'");
            addColumnIfMissing(connection, "bet_intents", "competition_name", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "selection_side", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
            addColumnIfMissing(connection, "bet_intents", "strategy_name", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "side", "TEXT NOT NULL DEFAULT 'BACK'");
            addColumnIfMissing(connection, "bet_intents", "result_message", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "external_order_id", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "effective_available_balance", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "reserved_balance", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "balance_snapshot_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "settled_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "settlement_result", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "realized_profit_loss", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "evaluation_id", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "recommendation_id", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "recommended_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "recommended_odds", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "order_submitted_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "order_response_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "order_accepted_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "executed_at", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "requested_odds", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "average_executed_odds", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "requested_stake", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "matched_stake", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "remaining_stake", "TEXT");
            addColumnIfMissing(connection, "bet_intents", "execution_status", "TEXT");
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_intents_active
                ON bet_intents(exchange, market_id, selection_id, stage, created_at DESC)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_intents_evaluation_id
                ON bet_intents(evaluation_id)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_intents_recommendation_id
                ON bet_intents(recommendation_id)
                """);
            statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_bet_intents_external_order_id
                ON bet_intents(external_order_id)
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bet_intent_deduplication_keys (
                    exchange TEXT NOT NULL,
                    market_id TEXT NOT NULL,
                    selection_id INTEGER NOT NULL,
                    side TEXT NOT NULL,
                    bet_intent_id TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (exchange, market_id, selection_id, side)
                )
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
            resultSet.getString("competition_name"),
            selectionSide(resultSet.getString("selection_side")),
            resultSet.getString("strategy_name"),
            side(resultSet.getString("side")),
            resultSet.getString("reason"),
            decimal(resultSet, "odds"),
            decimal(resultSet, "max_stake"),
            decimal(resultSet, "available_balance"),
            decimal(resultSet, "effective_available_balance"),
            decimal(resultSet, "reserved_balance"),
            instant(resultSet, "balance_snapshot_at"),
            decimal(resultSet, "selected_stake"),
            resultSet.getString("result_message"),
            resultSet.getString("external_order_id"),
            instant(resultSet, "settled_at"),
            settlementResult(resultSet.getString("settlement_result")),
            decimal(resultSet, "realized_profit_loss"),
            BetIntentStage.valueOf(resultSet.getString("stage")),
            Instant.parse(resultSet.getString("created_at")),
            Instant.parse(resultSet.getString("updated_at")),
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

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setString(index, value == null ? null : value.toString());
    }

    private Instant instant(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null ? null : Instant.parse(value);
    }

    private BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null ? null : new BigDecimal(value);
    }

    private BetSide side(String value) {
        return value == null || value.isBlank() ? BetSide.BACK : BetSide.valueOf(value);
    }

    private SelectionSide selectionSide(String value) {
        return value == null || value.isBlank() ? SelectionSide.UNKNOWN : SelectionSide.valueOf(value);
    }

    private BetSettlementResult settlementResult(String value) {
        return value == null || value.isBlank() ? null : BetSettlementResult.valueOf(value);
    }

    private BetExecutionStatus executionStatus(String value) {
        return value == null || value.isBlank() ? null : BetExecutionStatus.valueOf(value);
    }
}
