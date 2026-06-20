package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DiagnosticsModel.DiagnosticFindingSeverity;
import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import com.betx.application.DiagnosticsModel.DiagnosticsRequest;
import com.betx.application.DiagnosticsModel.MatchStatus;
import com.betx.application.DiagnosticsModel.RealBetDiagnosticRow;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StorageConfig;
import com.betx.domain.order.BetExecutionStatus;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosticsServiceTest {
    private static final Instant T0 = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void matchesExactMarketSelectionOneToOneAndCalculatesNormalizedSettledMetrics() {
        DiagnosticsReport report = service(dataset(
            List.of(real("real-1", "m1", 10, T0.plusSeconds(60), "3.00", "10.00", BetSettlementResult.WIN, "20.00")),
            List.of(paper("paper-1", "m1", 10, T0, "2.90", "2.90", "5.00", BacktestOutcome.WIN, "9.50"))
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.coverage().matchedPairs()).isEqualTo(1);
        assertThat(report.coverage().realOnly()).isZero();
        assertThat(report.coverage().paperOnly()).isZero();
        assertThat(report.matchedPairs()).hasSize(1);
        assertThat(report.matchedPairs().getFirst().matchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(report.paperVsRealMetrics().averageRealVsPaperOddsDifference()).isEqualByComparingTo("0.10000000");
        assertThat(report.paperVsRealMetrics().paperPnlPerUnitStake()).isEqualByComparingTo("1.90000000");
        assertThat(report.paperVsRealMetrics().realPnlPerUnitStake()).isEqualByComparingTo("2.00000000");
        assertThat(report.paperVsRealMetrics().normalizedExecutionDifference()).isEqualByComparingTo("0.10000000");
        assertThat(report.paperVsRealMetrics().pnlComparisonProvenance()).isEqualTo(DiagnosticsDataProvenance.SQLITE_EXACT);
    }

    @Test
    void classifiesRealOnlyPaperOnlyAndAmbiguousWithoutNameFallback() {
        DiagnosticsReport report = service(dataset(
            List.of(
                real("real-only", "real-market", 10, T0, "2.00", "5.00", null, null),
                real("real-a", "shared", 42, T0.plusSeconds(30), "2.00", "5.00", null, null),
                real("real-b", "shared", 42, T0.plusSeconds(40), "2.00", "5.00", null, null)
            ),
            List.of(
                paper("paper-only", "paper-market", 10, T0, "2.00", "2.00", "5.00", null, null),
                paper("paper-a", "shared", 42, T0, "2.00", "2.00", "5.00", null, null)
            )
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.coverage().matchedPairs()).isZero();
        assertThat(report.coverage().realOnly()).isEqualTo(1);
        assertThat(report.coverage().paperOnly()).isEqualTo(1);
        assertThat(report.coverage().ambiguous()).isEqualTo(1);
        assertThat(report.matchedPairs()).extracting(DiagnosticsMatch::matchStatus)
            .contains(MatchStatus.REAL_ONLY, MatchStatus.PAPER_ONLY, MatchStatus.AMBIGUOUS);
    }

    @Test
    void keepsOpenPairsOutOfPnlComparison() {
        DiagnosticsReport report = service(dataset(
            List.of(real("real-1", "m1", 10, T0, "2.00", "5.00", null, null)),
            List.of(paper("paper-1", "m1", 10, T0, "2.00", "2.00", "5.00", null, null))
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.coverage().matchedPairs()).isEqualTo(1);
        assertThat(report.paperVsRealMetrics().settledMatchedPairs()).isZero();
        assertThat(report.paperVsRealMetrics().normalizedExecutionDifference()).isNull();
    }

    @Test
    void usesOnlyLogCorrelatedEventsForExecutionLatency() {
        DiagnosticsLogSummary logs = new DiagnosticsLogSummary(
            Map.of("order.submitted", 1L, "order.accepted", 1L),
            Map.of("betfair-order-1", Duration.ofMillis(1500)),
            0,
            0,
            List.of()
        );

        DiagnosticsReport report = service(dataset(
            List.of(real("real-1", "m1", 10, T0, "2.00", "5.00", null, null, "betfair-order-1")),
            List.of()
        ), logs).generate(request());

        assertThat(report.executionMetrics().averageExecutionLatency()).isEqualTo(Duration.ofMillis(1500));
        assertThat(report.executionMetrics().latencyProvenance()).isEqualTo(DiagnosticsDataProvenance.LOG_CORRELATED);
    }

    @Test
    void flagsDuplicateAndSettlementInconsistenciesButNotHistoricalUnknowns() {
        DiagnosticsReport report = service(dataset(
            List.of(
                real("a", "dup", 10, T0, "2.00", "5.00", BetSettlementResult.WIN, "-1.00"),
                real("b", "dup", 10, T0.plusSeconds(1), "2.00", "5.00", BetSettlementResult.LOSE, "3.00"),
                historicalUnknown("old")
            ),
            List.of()
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.integrityFindings()).extracting(DiagnosticFinding::code)
            .contains("DUPLICATE_REAL_BETS", "WINNING_BET_NEGATIVE_PNL", "LOSING_BET_POSITIVE_PNL");
        assertThat(report.integrityFindings())
            .filteredOn(finding -> finding.severity() == DiagnosticFindingSeverity.ERROR)
            .isEmpty();
    }

    @Test
    void doesNotFlagHistoricalUnknownSelectionSideAsProspectiveMissingMetadata() {
        DiagnosticsReport report = service(dataset(
            List.of(
                recentHistoricalUnknown("historical-after-observability"),
                prospective("prospective-ok", SelectionSide.DRAW)
            ),
            List.of()
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.integrityFindings()).extracting(DiagnosticFinding::code)
            .doesNotContain("MISSING_SELECTION_SIDE_NEW_RECORDS", "MISSING_SELECTION_SIDE_PROSPECTIVE_RECORDS");
        assertThat(report.executionDataCoverage().prospectiveOrders()).isEqualTo(1);
        assertThat(report.executionDataCoverage().prospectiveWithSelectionSide()).isEqualTo(1);
        assertThat(report.executionDataCoverage().historicalUnknownSelectionSide()).isEqualTo(1);
    }

    @Test
    void formatterSeparatesLogEventsFromPersistedExecutionCoverage() {
        DiagnosticsLogSummary logs = new DiagnosticsLogSummary(
            Map.of("order.submitted", 9L, "order.accepted", 9L, "order.settled", 10L),
            Map.of(),
            0,
            0,
            List.of()
        );
        RealBetDiagnosticRow real = prospective("prospective-ok", SelectionSide.DRAW);

        DiagnosticsReport report = service(dataset(List.of(real), List.of()), logs).generate(request());

        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Operational events observed in logs")
            .contains("Persisted records in SQLite")
            .anySatisfy(line -> assertThat(line).contains("order.submitted events").contains("9"))
            .anySatisfy(line -> assertThat(line).contains("order.accepted events").contains("9"))
            .anySatisfy(line -> assertThat(line).contains("bets with order_submitted_at").contains("1 / 1"))
            .noneSatisfy(line -> assertThat(line).contains("Recommendations generated"));
    }

    private static DiagnosticsService service(DiagnosticsDataset dataset, DiagnosticsLogSummary logs) {
        return new DiagnosticsService(
            new TestConfigRepository(new BetxConfig(
                null,
                null,
                null,
                null,
                null,
                new StorageConfig("sqlite", "data/betx.db"),
                null,
                null,
                null,
                null,
                null
            )),
            new InMemoryDiagnosticsRepository(dataset),
            (logsDir, from, to) -> logs
        );
    }

    private static DiagnosticsRequest request() {
        return new DiagnosticsRequest(new ConfigPath(Path.of("betx.yml")), null, null, Path.of("logs"), Duration.ofHours(24));
    }

    private static DiagnosticsDataset dataset(List<RealBetDiagnosticRow> real, List<PaperTrade> paper) {
        return new DiagnosticsDataset(real, paper, 20, 40, Map.of("APPROVE", 2L), Map.of("INSUFFICIENT_EDGE", 3L));
    }

    private static RealBetDiagnosticRow real(
        String id,
        String marketId,
        long selectionId,
        Instant createdAt,
        String odds,
        String stake,
        BetSettlementResult result,
        String pnl
    ) {
        return real(id, marketId, selectionId, createdAt, odds, stake, result, pnl, null);
    }

    private static RealBetDiagnosticRow real(
        String id,
        String marketId,
        long selectionId,
        Instant createdAt,
        String odds,
        String stake,
        BetSettlementResult result,
        String pnl,
        String externalOrderId
    ) {
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            marketId,
            selectionId,
            "Event " + marketId,
            "Match Odds",
            "Runner " + selectionId,
            SelectionSide.DRAW,
            "League",
            "value-football",
            decimal(odds),
            decimal(stake),
            BetIntentStage.SETTLED,
            result,
            decimal(pnl),
            externalOrderId,
            createdAt,
            result == null ? null : createdAt.plusSeconds(3600),
            createdAt.plusSeconds(120),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(95),
            BigDecimal.valueOf(5),
            createdAt
        );
    }

    private static RealBetDiagnosticRow historicalUnknown(String id) {
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            "old",
            99,
            "Old Event",
            "Match Odds",
            "N/A",
            SelectionSide.UNKNOWN,
            "N/A",
            "N/A",
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            BetIntentStage.SETTLED,
            BetSettlementResult.WIN,
            BigDecimal.ONE,
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
            Instant.parse("2026-01-01T02:00:00Z"),
            null,
            null,
            null,
            null
        );
    }

    private static RealBetDiagnosticRow recentHistoricalUnknown(String id) {
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            "recent-historical",
            99,
            "Recent Historical Event",
            "Match Odds",
            "N/A",
            SelectionSide.UNKNOWN,
            "N/A",
            "N/A",
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            BetIntentStage.SETTLED,
            BetSettlementResult.WIN,
            BigDecimal.ONE,
            null,
            Instant.parse("2026-06-20T00:00:00Z"),
            Instant.parse("2026-06-20T01:00:00Z"),
            Instant.parse("2026-06-20T02:00:00Z"),
            null,
            null,
            null,
            null
        );
    }

    private static RealBetDiagnosticRow prospective(String id, SelectionSide selectionSide) {
        Instant createdAt = Instant.parse("2026-06-20T00:00:00Z");
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            "prospective",
            42,
            "Prospective Event",
            "Match Odds",
            "The Draw",
            selectionSide,
            "League",
            "value-football",
            BigDecimal.valueOf(3),
            BigDecimal.ONE,
            BetIntentStage.EXECUTED,
            null,
            null,
            "external-1",
            createdAt,
            null,
            createdAt,
            null,
            null,
            null,
            null,
            "eval-1",
            null,
            createdAt,
            BigDecimal.valueOf(3),
            createdAt,
            createdAt.plusMillis(250),
            null,
            null,
            BigDecimal.valueOf(3),
            null,
            BigDecimal.ONE,
            null,
            null,
            BetExecutionStatus.UNMATCHED
        );
    }

    private static PaperTrade paper(
        String id,
        String marketId,
        long selectionId,
        Instant recommendedAt,
        String requestedOdds,
        String executionOdds,
        String stake,
        BacktestOutcome result,
        String pnl
    ) {
        return new PaperTrade(
            id,
            "betfair",
            marketId,
            selectionId,
            "Event " + marketId,
            "Match Odds",
            "League",
            recommendedAt.plusSeconds(7200),
            "Runner " + selectionId,
            com.betx.domain.signal.BetSide.BACK,
            result == null ? PaperTradeStatus.EXECUTED : PaperTradeStatus.SETTLED,
            recommendedAt,
            decimal(requestedOdds),
            decimal(requestedOdds),
            recommendedAt.plusSeconds(5),
            decimal(executionOdds),
            true,
            recommendedAt.plusSeconds(3600),
            decimal(executionOdds).subtract(new BigDecimal("0.10")),
            result == null ? null : recommendedAt.plusSeconds(10800),
            result,
            decimal(stake),
            decimal(pnl),
            BigDecimal.ZERO,
            decimal(pnl),
            null,
            null,
            true
        );
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private record InMemoryDiagnosticsRepository(DiagnosticsDataset dataset) implements DiagnosticsRepository {
        @Override
        public DiagnosticsDataset load(String databasePath, Instant from, Instant to) {
            return dataset;
        }

        @Override
        public DiagnosticsPeriod findDefaultPeriod(String databasePath) {
            return new DiagnosticsPeriod(T0, T0.plusSeconds(86400));
        }
    }

    private record TestConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }
}
