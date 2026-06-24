package com.betx.adapter.persistence;

import com.betx.application.BacktestOutcome;
import com.betx.application.DiagnosticsBetRecommendationsSummary;
import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import com.betx.application.DiagnosticsModel.RealBetDiagnosticRow;
import com.betx.application.DiagnosticsPaperRecommendationCoverage;
import com.betx.application.DiagnosticsPeriod;
import com.betx.application.DiagnosticsRecommendationReadiness;
import com.betx.application.DiagnosticsRepository;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.BetExecutionStatus;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.BetSide;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdbcDiagnosticsRepository implements DiagnosticsRepository {
    private static final String DEFAULT_DATABASE_PATH = "./data/betx.db";

    @Override
    public DiagnosticsDataset load(String databasePath, Instant from, Instant to) {
        Path database = database(databasePath);
        if (!Files.exists(database)) {
            return new DiagnosticsDataset(List.of(), List.of(), 0, 0, Map.of(), Map.of());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            List<RealBetDiagnosticRow> realBets = tableExists(connection, "bet_intents")
                ? realBets(connection, from, to)
                : List.of();
            List<PaperTrade> paperTrades = tableExists(connection, "paper_trades")
                ? paperTrades(connection, from, to)
                : List.of();
            long marketsScanned = tableExists(connection, "market_snapshots")
                ? countDistinctMarkets(connection, from, to)
                : 0;
            long runnersAnalyzed = tableExists(connection, "paper_signal_evaluations")
                ? countPaperEvaluations(connection, from, to)
                : 0;
            Map<String, Long> recommendations = tableExists(connection, "signal_history")
                ? groupedCount(connection, "signal_history", "recommendation", "observed_at", from, to)
                : Map.of();
            Map<String, Long> rejections = tableExists(connection, "paper_signal_evaluations")
                ? groupedCount(connection, "paper_signal_evaluations", "analyzer_reason", "observed_at", from, to)
                : Map.of();
            DiagnosticsBetRecommendationsSummary betRecommendations = tableExists(connection, "bet_recommendations")
                ? betRecommendations(connection, from, to)
                : DiagnosticsBetRecommendationsSummary.empty();
            DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage = tableExists(connection, "paper_trades")
                ? paperRecommendationCoverage(connection, from, to)
                : DiagnosticsPaperRecommendationCoverage.empty();
            DiagnosticsRecommendationReadiness recommendationReadiness = tableExists(connection, "bet_recommendations")
                ? recommendationReadiness(connection, from, to, paperRecommendationCoverage)
                : DiagnosticsRecommendationReadiness.empty();
            return new DiagnosticsDataset(
                realBets,
                paperTrades,
                marketsScanned,
                runnersAnalyzed,
                recommendations,
                rejections,
                betRecommendations,
                paperRecommendationCoverage,
                recommendationReadiness
            );
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read diagnostics data.", exc);
        }
    }

    @Override
    public DiagnosticsPeriod findDefaultPeriod(String databasePath) {
        Path database = database(databasePath);
        if (!Files.exists(database)) {
            return new DiagnosticsPeriod(null, null);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            List<Instant> instants = new ArrayList<>();
            if (tableExists(connection, "bet_intents")) {
                addBounds(connection, instants, "bet_intents", List.of("created_at", "updated_at", "settled_at"));
            }
            if (tableExists(connection, "paper_trades")) {
                addBounds(connection, instants, "paper_trades", List.of(
                    "recommendation_timestamp",
                    "execution_timestamp",
                    "settlement_timestamp"
                ));
            }
            if (instants.isEmpty()) {
                return new DiagnosticsPeriod(null, null);
            }
            instants.sort(Instant::compareTo);
            return new DiagnosticsPeriod(instants.getFirst(), instants.getLast());
        } catch (SQLException exc) {
            throw new IllegalStateException("Could not read diagnostics default period.", exc);
        }
    }

    private static List<RealBetDiagnosticRow> realBets(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = """
            SELECT id, source, exchange, market_id, selection_id, event_name, market_name, runner_name,
                   competition_name, selection_side, strategy_name, odds, selected_stake, stage,
                   settlement_result, realized_profit_loss, external_order_id, created_at, settled_at, updated_at,
                   available_balance, effective_available_balance, reserved_balance, balance_snapshot_at,
                   %s AS evaluation_id, %s AS recommendation_id, %s AS recommended_at, %s AS recommended_odds,
                   %s AS order_submitted_at, %s AS order_response_at, %s AS order_accepted_at, %s AS executed_at,
                   %s AS requested_odds, %s AS average_executed_odds, %s AS requested_stake, %s AS matched_stake,
                   %s AS remaining_stake, %s AS execution_status
            FROM bet_intents
            WHERE (%s)
            ORDER BY created_at ASC, id ASC
            """.formatted(
                columnOrNull(connection, "bet_intents", "evaluation_id"),
                columnOrNull(connection, "bet_intents", "recommendation_id"),
                columnOrNull(connection, "bet_intents", "recommended_at"),
                columnOrNull(connection, "bet_intents", "recommended_odds"),
                columnOrNull(connection, "bet_intents", "order_submitted_at"),
                columnOrNull(connection, "bet_intents", "order_response_at"),
                columnOrNull(connection, "bet_intents", "order_accepted_at"),
                columnOrNull(connection, "bet_intents", "executed_at"),
                columnOrNull(connection, "bet_intents", "requested_odds"),
                columnOrNull(connection, "bet_intents", "average_executed_odds"),
                columnOrNull(connection, "bet_intents", "requested_stake"),
                columnOrNull(connection, "bet_intents", "matched_stake"),
                columnOrNull(connection, "bet_intents", "remaining_stake"),
                columnOrNull(connection, "bet_intents", "execution_status"),
                periodPredicate(List.of("created_at", "updated_at", "settled_at"))
            );
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RealBetDiagnosticRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new RealBetDiagnosticRow(
                        resultSet.getString("id"),
                        resultSet.getString("exchange"),
                        resultSet.getString("market_id"),
                        resultSet.getLong("selection_id"),
                        resultSet.getString("event_name"),
                        resultSet.getString("market_name"),
                        resultSet.getString("runner_name"),
                        selectionSide(resultSet.getString("selection_side")),
                        resultSet.getString("competition_name"),
                        resultSet.getString("strategy_name"),
                        decimal(resultSet, "odds"),
                        decimal(resultSet, "selected_stake"),
                        stage(resultSet.getString("stage")),
                        settlement(resultSet.getString("settlement_result")),
                        decimal(resultSet, "realized_profit_loss"),
                        resultSet.getString("external_order_id"),
                        instant(resultSet, "created_at"),
                        instant(resultSet, "settled_at"),
                        instant(resultSet, "updated_at"),
                        decimal(resultSet, "available_balance"),
                        decimal(resultSet, "effective_available_balance"),
                        decimal(resultSet, "reserved_balance"),
                        instant(resultSet, "balance_snapshot_at"),
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
                    ));
                }
                return rows;
            }
        }
    }

    private static List<PaperTrade> paperTrades(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = """
            SELECT *
            FROM paper_trades
            WHERE (%s)
            ORDER BY recommendation_timestamp ASC, id ASC
            """.formatted(periodPredicate(List.of("recommendation_timestamp", "execution_timestamp", "settlement_timestamp")));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PaperTrade> rows = new ArrayList<>();
                while (resultSet.next()) {
            rows.add(new PaperTrade(
                        resultSet.getString("id"),
                        resultSet.getString("exchange"),
                        resultSet.getString("market_id"),
                        resultSet.getLong("selection_id"),
                        resultSet.getString("event_name"),
                        resultSet.getString("market_name"),
                        resultSet.getString("league"),
                        instant(resultSet, "market_start_time"),
                        resultSet.getString("runner_name"),
                        side(resultSet.getString("side")),
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
                        resultSet.getInt("paper_mode") == 1,
                        hasColumn(connection, "paper_trades", "recommendation_id") ? resultSet.getString("recommendation_id") : null
                    ));
                }
                return rows;
            }
        }
    }

    private static DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage(
        Connection connection,
        Instant from,
        Instant to
    ) throws SQLException {
        long total = countPaperTrades(connection, from, to, "");
        if (!hasColumn(connection, "paper_trades", "recommendation_id")) {
            return new DiagnosticsPaperRecommendationCoverage(total, 0, total, 0, 0, 0, 0, 0, 0, 0);
        }
        long withRecommendationId = countPaperTrades(connection, from, to, "AND p.recommendation_id IS NOT NULL");
        long missingRecommendation = tableExists(connection, "bet_recommendations")
            ? countPaperTrades(connection, from, to, """
                AND p.recommendation_id IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1
                    FROM bet_recommendations br
                    WHERE br.id = p.recommendation_id
                )
                """)
            : withRecommendationId;
        long linkedCanonical = tableExists(connection, "bet_recommendations")
            ? countPaperTrades(connection, from, to, """
                AND EXISTS (
                    SELECT 1
                    FROM bet_recommendations br
                    WHERE br.id = p.recommendation_id
                      AND br.canonical_key IS NOT NULL
                )
                """)
            : 0;
        long linkedActive = countPaperTradesLinkedToStatus(connection, from, to, "ACTIVE");
        long linkedCovered = countPaperTradesLinkedToStatus(connection, from, to, "COVERED");
        long linkedExpired = countPaperTradesLinkedToStatus(connection, from, to, "EXPIRED");
        Instant firstLinkedPaperTrade = firstPaperTradeWithRecommendationId(connection, from, to);
        long post23PaperTrades = firstLinkedPaperTrade == null
            ? 0
            : countPaperTradesSince(connection, from, to, firstLinkedPaperTrade, "");
        long post23PaperTradesWithRecommendationId = firstLinkedPaperTrade == null
            ? 0
            : countPaperTradesSince(connection, from, to, firstLinkedPaperTrade, "AND p.recommendation_id IS NOT NULL");
        return new DiagnosticsPaperRecommendationCoverage(
            total,
            withRecommendationId,
            total - withRecommendationId,
            post23PaperTrades,
            post23PaperTradesWithRecommendationId,
            missingRecommendation,
            linkedCanonical,
            linkedActive,
            linkedCovered,
            linkedExpired
        );
    }

    private static Instant firstPaperTradeWithRecommendationId(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT MIN(p.recommendation_timestamp) AS first_linked
            FROM paper_trades p
            WHERE (%s)
              AND p.recommendation_id IS NOT NULL
            """.formatted(periodPredicate(List.of(
            "p.recommendation_timestamp",
            "p.execution_timestamp",
            "p.settlement_timestamp"
        ))))) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                String firstLinked = resultSet.getString("first_linked");
                return firstLinked == null ? null : Instant.parse(firstLinked);
            }
        }
    }

    private static long countPaperTrades(Connection connection, Instant from, Instant to, String extraPredicate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM paper_trades p
            WHERE (%s)
            %s
            """.formatted(
            periodPredicate(List.of("p.recommendation_timestamp", "p.execution_timestamp", "p.settlement_timestamp")),
            extraPredicate == null ? "" : extraPredicate
        ))) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countPaperTradesSince(
        Connection connection,
        Instant from,
        Instant to,
        Instant since,
        String extraPredicate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM paper_trades p
            WHERE (%s)
              AND p.recommendation_timestamp >= ?
            %s
            """.formatted(
            periodPredicate(List.of("p.recommendation_timestamp", "p.execution_timestamp", "p.settlement_timestamp")),
            extraPredicate == null ? "" : extraPredicate
        ))) {
            bindPeriod(statement, from, to, 3);
            statement.setString(13, since.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countPaperTradesLinkedToStatus(
        Connection connection,
        Instant from,
        Instant to,
        String status
    ) throws SQLException {
        if (!tableExists(connection, "bet_recommendations") || !hasColumn(connection, "paper_trades", "recommendation_id")) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM paper_trades p
            JOIN bet_recommendations br ON br.id = p.recommendation_id
            WHERE (%s)
              AND br.canonical_key IS NOT NULL
              AND br.status = ?
            """.formatted(periodPredicate(List.of(
            "p.recommendation_timestamp",
            "p.execution_timestamp",
            "p.settlement_timestamp"
        ))))) {
            bindPeriod(statement, from, to, 3);
            statement.setString(13, status);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static DiagnosticsRecommendationReadiness recommendationReadiness(
        Connection connection,
        Instant from,
        Instant to,
        DiagnosticsPaperRecommendationCoverage paperCoverage
    ) throws SQLException {
        if (!hasColumn(connection, "bet_recommendations", "canonical_key")) {
            return DiagnosticsRecommendationReadiness.empty();
        }
        boolean exactRealEquivalenceAvailable = tableExists(connection, "bet_intents")
            && hasColumn(connection, "bet_intents", "selection_side")
            && hasColumn(connection, "bet_intents", "strategy_name");
        RecommendationLinkCounts links = recommendationLinkCounts(connection, from, to, exactRealEquivalenceAvailable);
        long realBetsWithRecommendationId = tableExists(connection, "bet_intents") && hasColumn(connection, "bet_intents", "recommendation_id")
            ? countRealBets(connection, from, to, "AND b.recommendation_id IS NOT NULL")
            : 0;
        long realBetsTotal = tableExists(connection, "bet_intents")
            ? countRealBets(connection, from, to, "")
            : 0;
        return new DiagnosticsRecommendationReadiness(
            links.totalCanonicalRecommendations(),
            links.activeRecommendations(),
            links.coveredRecommendations(),
            links.expiredRecommendations(),
            links.recommendationsWithPaperTrades(),
            links.totalCanonicalRecommendations() - links.recommendationsWithPaperTrades(),
            links.recommendationsWithRealEquivalentBet(),
            links.totalCanonicalRecommendations() - links.recommendationsWithRealEquivalentBet(),
            links.recommendationsWithBothPaperAndRealEquivalent(),
            links.recommendationsWithPaperTrades() - links.recommendationsWithBothPaperAndRealEquivalent(),
            links.recommendationsWithRealEquivalentBet() - links.recommendationsWithBothPaperAndRealEquivalent(),
            links.totalCanonicalRecommendations()
                - links.recommendationsWithPaperTrades()
                - links.recommendationsWithRealEquivalentBet()
                + links.recommendationsWithBothPaperAndRealEquivalent(),
            paperCoverage.paperTradesWithRecommendationId(),
            paperCoverage.post23PaperTrades() - paperCoverage.post23PaperTradesWithRecommendationId(),
            paperCoverage.paperTradesWithRecommendationIdButMissingBetRecommendation(),
            realBetsWithRecommendationId,
            realBetsTotal - realBetsWithRecommendationId,
            exactRealEquivalenceAvailable ? DiagnosticsDataProvenance.SQLITE_EXACT : DiagnosticsDataProvenance.UNAVAILABLE,
            "PARTIAL",
            "NO",
            "PARTIAL",
            List.of()
        );
    }

    private static RecommendationLinkCounts recommendationLinkCounts(
        Connection connection,
        Instant from,
        Instant to,
        boolean includeRealEquivalent
    ) throws SQLException {
        String realEquivalentCte = includeRealEquivalent ? """
            real_equivalent AS (
                SELECT DISTINCT br.id
                FROM canonical br
                JOIN bet_intents b
                  ON b.exchange = br.exchange
                 AND b.market_id = br.market_id
                 AND b.selection_id = br.selection_id
                 AND b.selection_side = br.selection_side
                 AND b.strategy_name = br.strategy_name
                WHERE (%s)
            ),
            """.formatted(periodPredicate(List.of("b.created_at", "b.updated_at", "b.settled_at"))) : """
            real_equivalent AS (
                SELECT NULL AS id
                WHERE 0
            ),
            """;
        String sql = """
            WITH canonical AS (
                SELECT id, exchange, market_id, selection_id, selection_side, strategy_name, status
                FROM bet_recommendations
                WHERE canonical_key IS NOT NULL
                  AND (%s)
            ),
            paper_linked AS (
                SELECT DISTINCT br.id
                FROM canonical br
                JOIN paper_trades p ON p.recommendation_id = br.id
                WHERE (%s)
            ),
            %s
            classified AS (
                SELECT
                    c.id,
                    c.status,
                    EXISTS (SELECT 1 FROM paper_linked p WHERE p.id = c.id) AS has_paper,
                    EXISTS (SELECT 1 FROM real_equivalent r WHERE r.id = c.id) AS has_real
                FROM canonical c
            )
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active,
                SUM(CASE WHEN status = 'COVERED' THEN 1 ELSE 0 END) AS covered,
                SUM(CASE WHEN status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired,
                SUM(CASE WHEN has_paper THEN 1 ELSE 0 END) AS with_paper,
                SUM(CASE WHEN has_real THEN 1 ELSE 0 END) AS with_real,
                SUM(CASE WHEN has_paper AND has_real THEN 1 ELSE 0 END) AS both
            FROM classified
            """.formatted(
            periodPredicate(List.of("recommended_at", "first_seen_at", "last_seen_at")),
            periodPredicate(List.of("p.recommendation_timestamp", "p.execution_timestamp", "p.settlement_timestamp")),
            realEquivalentCte
        );
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to, 3);
            bindPeriod(statement, from, to, 3, 13);
            if (includeRealEquivalent) {
                bindPeriod(statement, from, to, 3, 25);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return RecommendationLinkCounts.empty();
                }
                return new RecommendationLinkCounts(
                    resultSet.getLong("total"),
                    resultSet.getLong("active"),
                    resultSet.getLong("covered"),
                    resultSet.getLong("expired"),
                    resultSet.getLong("with_paper"),
                    resultSet.getLong("with_real"),
                    resultSet.getLong("both")
                );
            }
        }
    }

    private static long countRealBets(Connection connection, Instant from, Instant to, String extraPredicate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM bet_intents b
            WHERE (%s)
            %s
            """.formatted(
            periodPredicate(List.of("b.created_at", "b.updated_at", "b.settled_at")),
            extraPredicate == null ? "" : extraPredicate
        ))) {
            bindPeriod(statement, from, to, 3);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countDistinctMarkets(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(DISTINCT exchange || '|' || market_id) AS total
            FROM market_snapshots
            WHERE (%s)
            """.formatted(periodPredicate(List.of("observed_at"))))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countPaperEvaluations(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM paper_signal_evaluations
            WHERE (%s)
            """.formatted(periodPredicate(List.of("observed_at"))))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static Map<String, Long> groupedCount(
        Connection connection,
        String table,
        String groupColumn,
        String timestampColumn,
        Instant from,
        Instant to
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(NULLIF(%s, ''), 'UNKNOWN') AS name, COUNT(*) AS total
            FROM %s
            WHERE (%s)
            GROUP BY COALESCE(NULLIF(%s, ''), 'UNKNOWN')
            ORDER BY total DESC
            """.formatted(groupColumn, table, periodPredicate(List.of(timestampColumn)), groupColumn))) {
            bindPeriod(statement, from, to, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, Long> values = new LinkedHashMap<>();
                while (resultSet.next()) {
                    values.put(resultSet.getString("name"), resultSet.getLong("total"));
                }
                return values;
            }
        }
    }

    private static DiagnosticsBetRecommendationsSummary betRecommendations(
        Connection connection,
        Instant from,
        Instant to
    ) throws SQLException {
        long total = countRecommendations(connection, from, to, "1 = 1");
        boolean hasCanonicalFields = hasColumn(connection, "bet_recommendations", "canonical_key")
            && hasColumn(connection, "bet_recommendations", "observed_count");
        long pre22ShadowRows = hasCanonicalFields
            ? countRecommendations(connection, from, to, "canonical_key IS NULL OR canonical_key = ''")
            : total;
        long post22CanonicalRows = hasCanonicalFields
            ? countRecommendations(connection, from, to, "canonical_key IS NOT NULL AND canonical_key <> ''")
            : 0;
        long activeCanonical = hasCanonicalFields
            ? countRecommendations(connection, from, to, "canonical_key IS NOT NULL AND canonical_key <> '' AND status = 'ACTIVE'")
            : 0;
        long coveredCanonical = hasCanonicalFields
            ? countRecommendations(connection, from, to, "canonical_key IS NOT NULL AND canonical_key <> '' AND status = 'COVERED'")
            : 0;
        long expiredCanonical = hasCanonicalFields
            ? countRecommendations(connection, from, to, "canonical_key IS NOT NULL AND canonical_key <> '' AND status = 'EXPIRED'")
            : 0;
        long observations = hasCanonicalFields ? recommendationObservationSum(connection, from, to) : 0;
        long withEvaluationId = countRecommendations(connection, from, to, "evaluation_id IS NOT NULL AND evaluation_id <> ''");
        long withLastEvaluationId = hasCanonicalFields
            ? countRecommendations(connection, from, to, "last_evaluation_id IS NOT NULL AND last_evaluation_id <> ''")
            : 0;
        long withStrategyName = countRecommendations(connection, from, to, "strategy_name IS NOT NULL AND strategy_name <> ''");
        long withSelectionSide = countRecommendations(connection, from, to, "selection_side IS NOT NULL AND selection_side <> '' AND selection_side <> 'UNKNOWN'");
        long orphanRecommendations = countRecommendations(connection, from, to, "evaluation_id IS NULL OR evaluation_id = ''");
        return new DiagnosticsBetRecommendationsSummary(
            total,
            pre22ShadowRows,
            post22CanonicalRows,
            activeCanonical,
            coveredCanonical,
            expiredCanonical,
            observations,
            hasCanonicalFields ? recommendationAverageObservedCount(connection, from, to) : 0,
            hasCanonicalFields ? recommendationPercentileObservedCount(connection, from, to, 0.50) : 0,
            hasCanonicalFields ? recommendationPercentileObservedCount(connection, from, to, 0.95) : 0,
            hasCanonicalFields ? topRecommendationsByObservedCount(connection, from, to) : Map.of(),
            hasCanonicalFields ? duplicateCanonicalGroups(connection, from, to) : 0,
            withEvaluationId,
            withLastEvaluationId,
            withStrategyName,
            withSelectionSide,
            groupedCount(connection, "bet_recommendations", "strategy_name", "recommended_at", from, to),
            groupedCount(connection, "bet_recommendations", "selection_side", "recommended_at", from, to),
            groupedCount(connection, "bet_recommendations", "competition_name", "recommended_at", from, to),
            total,
            orphanRecommendations
        );
    }

    private static long recommendationObservationSum(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(SUM(COALESCE(observed_count, 1)), 0) AS total
            FROM bet_recommendations
            WHERE (%s) AND canonical_key IS NOT NULL AND canonical_key <> ''
            """.formatted(periodPredicate(List.of("recommended_at", "created_at"))))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static double recommendationAverageObservedCount(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(AVG(COALESCE(observed_count, 1)), 0) AS average_value
            FROM bet_recommendations
            WHERE (%s) AND canonical_key IS NOT NULL AND canonical_key <> ''
            """.formatted(periodPredicate(List.of("recommended_at", "created_at"))))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("average_value") : 0;
            }
        }
    }

    private static double recommendationPercentileObservedCount(
        Connection connection,
        Instant from,
        Instant to,
        double percentile
    ) throws SQLException {
        long total = countRecommendations(connection, from, to, "canonical_key IS NOT NULL AND canonical_key <> ''");
        if (total == 0) {
            return 0;
        }
        long offset = Math.max(0, (long) Math.ceil(total * percentile) - 1);
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(observed_count, 1) AS observed_count
            FROM bet_recommendations
            WHERE (%s) AND canonical_key IS NOT NULL AND canonical_key <> ''
            ORDER BY COALESCE(observed_count, 1) ASC
            LIMIT 1 OFFSET ?
            """.formatted(periodPredicate(List.of("recommended_at", "created_at"))))) {
            bindPeriod(statement, from, to, 2);
            statement.setLong(9, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("observed_count") : 0;
            }
        }
    }

    private static Map<String, Long> topRecommendationsByObservedCount(
        Connection connection,
        Instant from,
        Instant to
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT canonical_key AS name, COALESCE(observed_count, 1) AS total
            FROM bet_recommendations
            WHERE (%s) AND canonical_key IS NOT NULL AND canonical_key <> ''
            ORDER BY COALESCE(observed_count, 1) DESC, canonical_key ASC
            LIMIT 5
            """.formatted(periodPredicate(List.of("recommended_at", "created_at"))))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, Long> values = new LinkedHashMap<>();
                while (resultSet.next()) {
                    values.put(resultSet.getString("name"), resultSet.getLong("total"));
                }
                return values;
            }
        }
    }

    private static long duplicateCanonicalGroups(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM (
                SELECT canonical_key
                FROM bet_recommendations
                WHERE (%s) AND canonical_key IS NOT NULL AND canonical_key <> ''
                GROUP BY canonical_key
                HAVING COUNT(*) > 1
            )
            """.formatted(periodPredicate(List.of("recommended_at", "created_at"))))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static long countRecommendations(
        Connection connection,
        Instant from,
        Instant to,
        String extraPredicate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS total
            FROM bet_recommendations
            WHERE (%s) AND (%s)
            """.formatted(periodPredicate(List.of("recommended_at", "created_at")), extraPredicate))) {
            bindPeriod(statement, from, to, 2);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private static void addBounds(Connection connection, List<Instant> instants, String table, List<String> columns) throws SQLException {
        for (String column : columns) {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT MIN(" + column + ") AS min_value, MAX(" + column + ") AS max_value FROM " + table)) {
                if (resultSet.next()) {
                    addInstant(instants, resultSet.getString("min_value"));
                    addInstant(instants, resultSet.getString("max_value"));
                }
            }
        }
    }

    private static void addInstant(List<Instant> instants, String value) {
        if (value != null && !value.isBlank()) {
            instants.add(Instant.parse(value));
        }
    }

    private static String periodPredicate(List<String> columns) {
        return columns.stream()
            .map(column -> "(" + column + " IS NOT NULL AND (? IS NULL OR " + column + " >= ?) AND (? IS NULL OR " + column + " <= ?))")
            .collect(java.util.stream.Collectors.joining(" OR "));
    }

    private static void bindPeriod(PreparedStatement statement, Instant from, Instant to, int timestampColumns) throws SQLException {
        bindPeriod(statement, from, to, timestampColumns, 1);
    }

    private static void bindPeriod(
        PreparedStatement statement,
        Instant from,
        Instant to,
        int timestampColumns,
        int startIndex
    ) throws SQLException {
        int index = startIndex;
        for (int ignored = 0; ignored < timestampColumns; ignored++) {
            statement.setString(index++, from == null ? null : from.toString());
            statement.setString(index++, from == null ? null : from.toString());
            statement.setString(index++, to == null ? null : to.toString());
            statement.setString(index++, to == null ? null : to.toString());
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, null)) {
            return resultSet.next();
        }
    }

    private static String columnOrNull(Connection connection, String table, String column) throws SQLException {
        return hasColumn(connection, table, column) ? column : "NULL";
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, table, column)) {
            return resultSet.next();
        }
    }

    private static Path database(String databasePath) {
        return Path.of(databasePath == null || databasePath.isBlank() ? DEFAULT_DATABASE_PATH : databasePath)
            .toAbsolutePath()
            .normalize();
    }

    private static Instant instant(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static BigDecimal decimal(ResultSet resultSet, String field) throws SQLException {
        String value = resultSet.getString(field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static BetIntentStage stage(String value) {
        return value == null || value.isBlank() ? null : BetIntentStage.valueOf(value);
    }

    private static BetSettlementResult settlement(String value) {
        return value == null || value.isBlank() ? null : BetSettlementResult.valueOf(value);
    }

    private static BetExecutionStatus executionStatus(String value) {
        return value == null || value.isBlank() ? null : BetExecutionStatus.valueOf(value);
    }

    private static SelectionSide selectionSide(String value) {
        return value == null || value.isBlank() ? SelectionSide.UNKNOWN : SelectionSide.valueOf(value);
    }

    private static BetSide side(String value) {
        return value == null || value.isBlank() ? BetSide.BACK : BetSide.valueOf(value);
    }

    private static BacktestOutcome outcome(String value) {
        return value == null || value.isBlank() ? null : BacktestOutcome.valueOf(value);
    }

    private record RecommendationLinkCounts(
        long totalCanonicalRecommendations,
        long activeRecommendations,
        long coveredRecommendations,
        long expiredRecommendations,
        long recommendationsWithPaperTrades,
        long recommendationsWithRealEquivalentBet,
        long recommendationsWithBothPaperAndRealEquivalent
    ) {
        private static RecommendationLinkCounts empty() {
            return new RecommendationLinkCounts(0, 0, 0, 0, 0, 0, 0);
        }
    }
}
