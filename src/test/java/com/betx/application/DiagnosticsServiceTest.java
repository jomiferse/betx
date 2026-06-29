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
            Map.of(
                "order.submitted", 9L,
                "order.response", 8L,
                "order.accepted", 1L,
                "order.unmatched", 2L,
                "order.matched", 5L,
                "order.settled", 10L,
                "bet_signal.skipped:ACTIVE_MARKET_INTENT_EXISTS", 34L,
                "bet_intent.skipped:DUPLICATE_REAL_BET", 1L
            ),
            Map.of(),
            List.of(new DiagnosticsSkippedMarket(
                "Team A v Team B",
                "Team A",
                "1.1",
                42L,
                "BACK",
                "intent-1",
                "FULLY_MATCHED",
                34L
            )),
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
            .anySatisfy(line -> assertThat(line).contains("order.response events").contains("8"))
            .anySatisfy(line -> assertThat(line).contains("legacy order.accepted events").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("order.unmatched events").contains("2"))
            .anySatisfy(line -> assertThat(line).contains("order.matched events").contains("5"))
            .anySatisfy(line -> assertThat(line).contains("Early active-market skips").contains("34"))
            .anySatisfy(line -> assertThat(line).contains("Atomic duplicate blocks").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Team A v Team B / Team A / attempts 34"))
            .anySatisfy(line -> assertThat(line).contains("bets with order_submitted_at").contains("1 / 1"))
            .noneSatisfy(line -> assertThat(line).contains("Recommendations generated"));
    }

    @Test
    void reportsShadowRecommendationsWithoutChangingLegacyMatching() {
        DiagnosticsDataset dataset = new DiagnosticsDataset(
            List.of(real("real-1", "m1", 10, T0.plusSeconds(60), "3.00", "10.00", BetSettlementResult.WIN, "20.00")),
            List.of(paper("paper-1", "m1", 10, T0, "2.90", "2.90", "5.00", BacktestOutcome.WIN, "9.50")),
            20,
            40,
            Map.of("BET", 1L),
            Map.of(),
            new DiagnosticsBetRecommendationsSummary(
                3,
                1,
                2,
                1,
                1,
                0,
                7,
                3.5,
                3,
                5,
                Map.of("betfair|m1|10|HOME|value-football", 5L),
                0,
                3,
                2,
                3,
                3,
                Map.of("value-football", 3L),
                Map.of("HOME", 2L, "DRAW", 1L),
                Map.of("La Liga", 3L),
                3,
                0
            )
        );

        DiagnosticsReport report = service(dataset, DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.coverage().matchedPairs()).isEqualTo(1);
        assertThat(report.betRecommendations().totalRecommendations()).isEqualTo(3);
        assertThat(report.betRecommendations().byStrategy()).containsEntry("value-football", 3L);
        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Bet recommendations")
            .anySatisfy(line -> assertThat(line).contains("Total recommendations").contains("3"))
            .anySatisfy(line -> assertThat(line).contains("pre-2.2 shadow rows").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("post-2.2 canonical rows").contains("2"))
            .anySatisfy(line -> assertThat(line).contains("Canonical covered recommendations").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation observations").contains("7"))
            .anySatisfy(line -> assertThat(line).contains("Recommendations with evaluation_id").contains("3 / 3"))
            .anySatisfy(line -> assertThat(line).contains("Recommendations with last_evaluation_id").contains("2 / 3"))
            .anySatisfy(line -> assertThat(line).contains("value-football").contains("3"))
            .anySatisfy(line -> assertThat(line)
                .contains("BetRecommendation is consumed by paper and real prospectively")
                .contains("recommendation_id matching is not enabled yet"))
            .noneSatisfy(line -> assertThat(line).contains("BetRecommendation is consumed by paper trading only"));
    }

    @Test
    void reportsPaperRecommendationCoverageWithoutChangingLegacyMatching() {
        DiagnosticsDataset dataset = new DiagnosticsDataset(
            List.of(real("real-1", "m1", 10, T0.plusSeconds(60), "3.00", "10.00", BetSettlementResult.WIN, "20.00")),
            List.of(
                paper("paper-linked", "m1", 10, T0, "2.90", "2.90", "5.00", BacktestOutcome.WIN, "9.50", "rec-canonical-active"),
                paper("paper-historical", "m2", 11, T0, "3.10", "3.10", "5.00", null, null)
            ),
            20,
            40,
            Map.of("BET", 1L),
            Map.of(),
            DiagnosticsBetRecommendationsSummary.empty(),
            new DiagnosticsPaperRecommendationCoverage(
                2,
                1,
                1,
                1,
                1,
                0,
                1,
                1,
                0,
                0
            )
        );

        DiagnosticsReport report = service(dataset, DiagnosticsLogSummary.empty()).generate(request());

        assertThat(report.coverage().matchedPairs()).isEqualTo(1);
        assertThat(report.paperRecommendationCoverage().paperTradesTotal()).isEqualTo(2);
        assertThat(report.paperRecommendationCoverage().paperTradesWithRecommendationId()).isEqualTo(1);
        assertThat(report.paperRecommendationCoverage().paperTradesWithoutRecommendationId()).isEqualTo(1);
        assertThat(report.paperRecommendationCoverage().paperTradesLinkedToCanonicalRecommendation()).isEqualTo(1);
        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Paper recommendation coverage")
            .anySatisfy(line -> assertThat(line).contains("Paper trades with recommendation_id").contains("1 / 2"))
            .anySatisfy(line -> assertThat(line).contains("Post-2.3 paper trades with recommendation_id").contains("1 / 1"))
            .anySatisfy(line -> assertThat(line).contains("Paper trades linked to ACTIVE recommendations").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Paper trades linked to EXPIRED recommendations").contains("0"))
            .anySatisfy(line -> assertThat(line).contains("BetRecommendation consumed by paper").contains("yes"))
            .anySatisfy(line -> assertThat(line).contains("BetRecommendation consumed by real").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"));
    }

    @Test
    void reportsRecommendationReadinessWithoutEnablingRecommendationIdMatching() {
        DiagnosticsDataset dataset = new DiagnosticsDataset(
            List.of(real("real-1", "m1", 10, T0.plusSeconds(60), "3.00", "10.00", BetSettlementResult.WIN, "20.00")),
            List.of(paper("paper-linked", "m1", 10, T0, "2.90", "2.90", "5.00", BacktestOutcome.WIN, "9.50", "rec-1")),
            20,
            40,
            Map.of("BET", 1L),
            Map.of(),
            new DiagnosticsBetRecommendationsSummary(
                2,
                0,
                2,
                1,
                1,
                0,
                3,
                1.5,
                1,
                2,
                Map.of(),
                0,
                2,
                2,
                2,
                2,
                Map.of(),
                Map.of(),
                Map.of(),
                2,
                0
            ),
            new DiagnosticsPaperRecommendationCoverage(1, 1, 0, 1, 1, 0, 1, 1, 0, 0),
            new DiagnosticsRecommendationReadiness(
                2,
                1,
                1,
                0,
                1,
                1,
                1,
                1,
                1,
                0,
                0,
                1,
                1,
                0,
                0,
                0,
                1,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                DiagnosticsDataProvenance.SQLITE_EXACT,
                "PARTIAL",
                "NO",
                "PARTIAL",
                List.of()
            )
        );

        DiagnosticsReport report = service(dataset, new DiagnosticsLogSummary(
            Map.of("order.submitted", 1L, "order.response", 1L),
            Map.of(),
            0,
            0,
            List.of()
        )).generate(request());

        assertThat(report.recommendationReadiness().readyForRecommendationIdMatching()).isEqualTo("NO");
        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Recommendation readiness")
            .anySatisfy(line -> assertThat(line).contains("Paper consumes BetRecommendation").contains("yes"))
            .anySatisfy(line -> assertThat(line).contains("Real consumes BetRecommendation").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Recommendations with both paper and real equivalent").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Ready for recommendation_id matching").contains("NO"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("No post-2.5 real bet sample with recommendation_id is available yet."));
    }

    @Test
    void recommendationReadinessShowsRealConsumptionAsMatchingCandidateWithoutChangingOfficialMatching() {
        DiagnosticsDataset dataset = new DiagnosticsDataset(
            List.of(),
            List.of(),
            0,
            0,
            Map.of(),
            Map.of(),
            DiagnosticsBetRecommendationsSummary.empty(),
            DiagnosticsPaperRecommendationCoverage.empty(),
            new DiagnosticsRecommendationReadiness(
                1,
                1,
                0,
                0,
                1,
                0,
                1,
                0,
                1,
                0,
                0,
                0,
                1,
                0,
                0,
                1,
                0,
                1,
                1,
                1,
                0,
                0,
                1,
                1,
                0,
                0,
                DiagnosticsDataProvenance.SQLITE_EXACT,
                "PARTIAL",
                "NO",
                "PARTIAL",
                List.of()
            )
        );

        DiagnosticsReport report = service(dataset, new DiagnosticsLogSummary(
            Map.of("order.submitted", 1L, "order.response", 1L),
            Map.of(),
            0,
            0,
            List.of()
        )).generate(request());

        assertThat(report.recommendationReadiness().readyForRealConsumption()).isEqualTo("YES");
        assertThat(report.recommendationReadiness().readyForRecommendationIdMatching()).isEqualTo("PARTIAL");
        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Real recommendation coverage")
            .anySatisfy(line -> assertThat(line).contains("Real consumes BetRecommendation").contains("yes"))
            .anySatisfy(line -> assertThat(line).contains("Post-2.5 real bets with recommendation_id").contains("1 / 1"))
            .anySatisfy(line -> assertThat(line).contains("Real bets linked to canonical recommendation").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Real bets linked to ACTIVE recommendations").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation_id matching official").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Legacy matching remains official").contains("yes"));
    }

    @Test
    void previewsRecommendationIdMatchingClassificationsWithoutChangingLegacyCounts() {
        DiagnosticsDataset dataset = dataset(
            List.of(
                realWithRecommendation("real-match", "m1", 10, T0.plusSeconds(1), "rec-match"),
                realWithRecommendation("real-only", "m2", 20, T0.plusSeconds(2), "rec-real-only"),
                realWithRecommendation("real-many-paper", "m3", 30, T0.plusSeconds(3), "rec-many-paper"),
                realWithRecommendation("real-many-a", "m4", 40, T0.plusSeconds(4), "rec-many-real"),
                realWithRecommendation("real-many-b", "m4", 40, T0.plusSeconds(5), "rec-many-real"),
                realWithRecommendation("real-many-many-a", "m5", 50, T0.plusSeconds(6), "rec-many-many"),
                realWithRecommendation("real-many-many-b", "m5", 50, T0.plusSeconds(7), "rec-many-many"),
                real("real-historical", "m6", 60, T0.plusSeconds(8), "2.00", "1.00", null, null)
            ),
            List.of(
                paper("paper-match", "m1", 10, T0, "2.00", "2.00", "1.00", null, null, "rec-match"),
                paper("paper-only", "m7", 70, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-only"),
                paper("paper-many-a", "m3", 30, T0, "2.00", "2.00", "1.00", null, null, "rec-many-paper"),
                paper("paper-many-b", "m3", 30, T0.plusSeconds(1), "2.00", "2.00", "1.00", null, null, "rec-many-paper"),
                paper("paper-many-real", "m4", 40, T0, "2.00", "2.00", "1.00", null, null, "rec-many-real"),
                paper("paper-many-many-a", "m5", 50, T0, "2.00", "2.00", "1.00", null, null, "rec-many-many"),
                paper("paper-many-many-b", "m5", 50, T0.plusSeconds(1), "2.00", "2.00", "1.00", null, null, "rec-many-many"),
                paper("paper-historical", "m8", 80, T0, "2.00", "2.00", "1.00", null, null)
            )
        );

        DiagnosticsReport report = service(dataset, DiagnosticsLogSummary.empty()).generate(request());
        DiagnosticsRecommendationIdMatchingScope preview = report.recommendationIdMatchingPreview().allTime();

        assertThat(report.coverage().matchedPairs()).isEqualTo(1);
        assertThat(report.coverage().realOnly()).isEqualTo(3);
        assertThat(report.coverage().paperOnly()).isEqualTo(3);
        assertThat(report.coverage().ambiguous()).isEqualTo(3);
        assertThat(report.recommendationIdMatchingPreview().enabledAsOfficialMatching()).isFalse();
        assertThat(preview.recommendationIdPairs()).isEqualTo(1);
        assertThat(preview.recommendationIdPaperOnly()).isEqualTo(1);
        assertThat(preview.recommendationIdRealOnly()).isEqualTo(1);
        assertThat(preview.recommendationIdAmbiguous()).isEqualTo(3);
        assertThat(preview.ambiguousManyPaperToOneReal()).isEqualTo(1);
        assertThat(preview.ambiguousOnePaperToManyReal()).isEqualTo(1);
        assertThat(preview.ambiguousManyToMany()).isEqualTo(1);
        assertThat(preview.paperTradesEligible()).isEqualTo(7);
        assertThat(preview.realBetsEligible()).isEqualTo(7);
        assertThat(preview.paperTradesWithRecommendationId()).isEqualTo(7);
        assertThat(preview.realBetsWithRecommendationId()).isEqualTo(7);
    }

    @Test
    void comparesLegacyAndRecommendationIdPreviewPairs() {
        DiagnosticsDataset dataset = dataset(
            List.of(
                realWithRecommendation("real-both", "same", 1, T0.plusSeconds(1), "rec-both"),
                realWithRecommendation("real-legacy-only", "legacy-only", 2, T0.plusSeconds(2), "rec-without-paper"),
                realWithRecommendation("real-rec-only", "rec-only-real", 3, T0.plusSeconds(3), "rec-only-preview"),
                realWithRecommendation("real-conflict-a", "conflict", 4, T0.plusSeconds(4), "rec-conflict-real"),
                realWithRecommendation("real-conflict-b", "other-conflict", 5, T0.plusSeconds(5), "rec-conflict-paper"),
                realWithRecommendation("real-ambiguous", "ambiguous", 6, T0.plusSeconds(6), "rec-ambiguous"),
                realWithRecommendation("real-rec-amb-a", "rec-amb", 7, T0.plusSeconds(7), "rec-ambiguous-preview"),
                realWithRecommendation("real-rec-amb-b", "rec-amb-other", 8, T0.plusSeconds(8), "rec-ambiguous-preview")
            ),
            List.of(
                paper("paper-both", "same", 1, T0, "2.00", "2.00", "1.00", null, null, "rec-both"),
                paper("paper-legacy-only", "legacy-only", 2, T0, "2.00", "2.00", "1.00", null, null, "rec-without-real"),
                paper("paper-rec-only", "rec-only-paper", 3, T0, "2.00", "2.00", "1.00", null, null, "rec-only-preview"),
                paper("paper-conflict-a", "conflict", 4, T0, "2.00", "2.00", "1.00", null, null, "rec-conflict-paper"),
                paper("paper-conflict-b", "other-conflict", 5, T0, "2.00", "2.00", "1.00", null, null, "rec-conflict-real"),
                paper("paper-ambiguous-a", "ambiguous", 6, T0, "2.00", "2.00", "1.00", null, null, "rec-ambiguous"),
                paper("paper-ambiguous-b", "ambiguous", 6, T0.plusSeconds(1), "2.00", "2.00", "1.00", null, null, "rec-other"),
                paper("paper-rec-amb", "rec-amb", 7, T0, "2.00", "2.00", "1.00", null, null, "rec-ambiguous-preview")
            )
        );

        DiagnosticsRecommendationLegacyComparison comparison = service(dataset, DiagnosticsLogSummary.empty())
            .generate(request())
            .recommendationIdMatchingPreview()
            .allTime()
            .legacyComparison();

        assertThat(comparison.legacyMatchedPairs()).isEqualTo(5);
        assertThat(comparison.recommendationIdMatchedPairs()).isEqualTo(5);
        assertThat(comparison.matchedByBoth()).isEqualTo(1);
        assertThat(comparison.legacyOnlyMatches()).isEqualTo(2);
        assertThat(comparison.recommendationOnlyMatches()).isEqualTo(2);
        assertThat(comparison.conflictingMatches()).isEqualTo(2);
        assertThat(comparison.legacyRealOnlyButRecommendationMatched()).isEqualTo(2);
        assertThat(comparison.legacyPaperOnlyButRecommendationMatched()).isEqualTo(2);
        assertThat(comparison.legacyAmbiguousResolvedByRecommendationId()).isEqualTo(1);
        assertThat(comparison.recommendationAmbiguousButLegacyMatched()).isEqualTo(1);
    }

    @Test
    void recommendationIdPreviewPost25UsesFirstRealRecommendationCutoff() {
        DiagnosticsDataset dataset = dataset(
            List.of(
                real("real-before", "before", 1, T0.minusSeconds(60), "2.00", "1.00", null, null),
                realWithRecommendation("real-post", "post", 2, T0.plusSeconds(10), "rec-post")
            ),
            List.of(
                paper("paper-before", "before", 1, T0.minusSeconds(120), "2.00", "2.00", "1.00", null, null, "rec-before"),
                paper("paper-post", "post", 2, T0.plusSeconds(15), "2.00", "2.00", "1.00", null, null, "rec-post")
            )
        );

        DiagnosticsRecommendationIdMatchingScope post25 = service(dataset, DiagnosticsLogSummary.empty())
            .generate(request())
            .recommendationIdMatchingPreview()
            .post25();

        assertThat(post25.cutoff()).isEqualTo(T0.plusSeconds(10));
        assertThat(post25.paperTradesTotal()).isEqualTo(1);
        assertThat(post25.realBetsTotal()).isEqualTo(1);
        assertThat(post25.recommendationIdPairs()).isEqualTo(1);
        assertThat(post25.paperTradesWithRecommendationId()).isEqualTo(1);
        assertThat(post25.realBetsWithRecommendationId()).isEqualTo(1);
    }

    @Test
    void formatsRecommendationIdMatchingPreviewWithoutEnablingOfficialMatching() {
        DiagnosticsReport report = service(dataset(
            List.of(realWithRecommendation("real-1", "m1", 10, T0.plusSeconds(1), "rec-1")),
            List.of(paper("paper-1", "m1", 10, T0, "2.00", "2.00", "1.00", null, null, "rec-1"))
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Recommendation-id matching preview")
            .anySatisfy(line -> assertThat(line).contains("Enabled as official matching").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Preview available").contains("yes"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation-id pairs").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation-id paper-only").contains("0"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation-id real-only").contains("0"))
            .anySatisfy(line -> assertThat(line).contains("Recommendation-id ambiguous").contains("0"))
            .anySatisfy(line -> assertThat(line).contains("Legacy comparison").contains("post-2.5"))
            .anySatisfy(line -> assertThat(line).contains("Conflicts").contains("0"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Legacy matching remains official").contains("yes"));
    }

    @Test
    void explainsRecommendationDivergenceFromExactRecommendationLogEvidence() {
        DiagnosticsLogSummary logs = logsWithEvents(
            logEvent("risk.blocked", "rec-paper-risk", "m-risk", 10, "MAX_OPEN_POSITIONS", T0.plusSeconds(20)),
            logEvent("bet_signal.skipped", "rec-paper-active", "m-active", 20, "ACTIVE_MARKET_INTENT_EXISTS", T0.plusSeconds(30)),
            logEvent("paper_trade.execution_failed", "rec-real-paper-failed", "m-paper-failed", 30, "NO_PRICE_AVAILABLE", T0.plusSeconds(40)),
            logEvent("dependency.error", "rec-real-dependency", "m-dependency", 40, "paper_market_scan", T0.plusSeconds(50))
        );
        DiagnosticsReport report = service(dataset(
            List.of(
                realWithRecommendation("real-match", "m-match", 1, T0.plusSeconds(1), "rec-match"),
                realWithRecommendation("real-paper-failed", "m-paper-failed", 30, T0.plusSeconds(2), "rec-real-paper-failed"),
                realWithRecommendation("real-dependency", "m-dependency", 40, T0.plusSeconds(3), "rec-real-dependency"),
                realWithRecommendation("real-no-paper", "m-no-paper", 50, T0.plusSeconds(4), "rec-real-no-paper")
            ),
            List.of(
                paper("paper-match", "m-match", 1, T0, "2.00", "2.00", "1.00", null, null, "rec-match"),
                paper("paper-risk", "m-risk", 10, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-risk"),
                paper("paper-active", "m-active", 20, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-active"),
                paper("paper-no-real", "m-no-real", 60, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-no-real")
            )
        ), logs).generate(request());

        DiagnosticsRecommendationDivergenceAnalysis divergence = report.recommendationDivergenceAnalysis();

        assertThat(divergence.paperOnlyRecommendations()).isEqualTo(3);
        assertThat(divergence.realOnlyRecommendations()).isEqualTo(3);
        assertThat(divergence.ambiguousRecommendations()).isZero();
        assertThat(divergence.paperOnlyReasonBreakdown())
            .containsEntry(DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_RISK_MAX_OPEN_POSITIONS, 1L)
            .containsEntry(DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_ACTIVE_MARKET_INTENT_EXISTS, 1L)
            .containsEntry(DiagnosticsRecommendationDivergenceReason.REAL_NOT_ATTEMPTED, 1L);
        assertThat(divergence.realOnlyReasonBreakdown())
            .containsEntry(DiagnosticsRecommendationDivergenceReason.PAPER_EXECUTION_FAILED, 1L)
            .containsEntry(DiagnosticsRecommendationDivergenceReason.PAPER_DEPENDENCY_ERROR, 1L)
            .containsEntry(DiagnosticsRecommendationDivergenceReason.PAPER_NOT_CREATED, 1L);
        assertThat(divergence.topPaperOnlyExamples())
            .filteredOn(example -> example.recommendationId().equals("rec-paper-risk"))
            .singleElement()
            .satisfies(example -> {
                assertThat(example.reason()).isEqualTo(DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_RISK_MAX_OPEN_POSITIONS);
                assertThat(example.evidence()).singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.eventName()).isEqualTo("risk.blocked");
                        assertThat(evidence.source()).isEqualTo(DiagnosticsModel.DiagnosticsDataProvenance.STRUCTURED_LOGS);
                    });
            });
        assertThat(divergence.topRealOnlyExamples())
            .filteredOn(example -> example.recommendationId().equals("rec-real-paper-failed"))
            .singleElement()
            .extracting(DiagnosticsRecommendationDivergenceExample::reason)
            .isEqualTo(DiagnosticsRecommendationDivergenceReason.PAPER_EXECUTION_FAILED);
    }

    @Test
    void exactRecommendationEvidenceWinsOverTemporalHeuristicAndOtherSelectionsAreIgnored() {
        DiagnosticsLogSummary logs = logsWithEvents(
            logEvent("risk.blocked", "other-rec", "m-risk", 10, "MAX_OPEN_POSITIONS", T0.plusSeconds(20)),
            logEvent("bet_signal.skipped", null, "other-market", 10, "ACTIVE_MARKET_INTENT_EXISTS", T0.plusSeconds(25)),
            logEvent("risk.blocked", "rec-paper-risk", "m-risk", 10, "DAILY_LOSS_LIMIT", T0.plusSeconds(30))
        );
        DiagnosticsReport report = service(dataset(
            List.of(),
            List.of(paper("paper-risk", "m-risk", 10, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-risk"))
        ), logs).generate(request());

        DiagnosticsRecommendationDivergenceExample example = report.recommendationDivergenceAnalysis()
            .topPaperOnlyExamples()
            .getFirst();

        assertThat(example.reason()).isEqualTo(DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_RISK_DAILY_LOSS);
        assertThat(example.evidence()).singleElement()
            .satisfies(evidence -> {
                assertThat(evidence.recommendationId()).isEqualTo("rec-paper-risk");
                assertThat(evidence.source()).isEqualTo(DiagnosticsModel.DiagnosticsDataProvenance.STRUCTURED_LOGS);
            });
    }

    @Test
    void temporalFallbackIsMarkedAsLegacyApproximation() {
        DiagnosticsLogSummary logs = logsWithEvents(
            logEvent("bet_signal.skipped", null, "m-active", 20, "ACTIVE_MARKET_INTENT_EXISTS", T0.plusSeconds(30))
        );
        DiagnosticsReport report = service(dataset(
            List.of(),
            List.of(paper("paper-active", "m-active", 20, T0, "2.00", "2.00", "1.00", null, null, "rec-paper-active"))
        ), logs).generate(request());

        DiagnosticsRecommendationDivergenceExample example = report.recommendationDivergenceAnalysis()
            .topPaperOnlyExamples()
            .getFirst();

        assertThat(example.reason()).isEqualTo(DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_ACTIVE_MARKET_INTENT_EXISTS);
        assertThat(example.evidence()).singleElement()
            .extracting(DiagnosticsRecommendationDivergenceEvidence::source)
            .isEqualTo(DiagnosticsModel.DiagnosticsDataProvenance.LEGACY_APPROXIMATION);
    }

    @Test
    void formatsRecommendationDivergenceAnalysisWithoutEnablingOfficialRecommendationMatching() {
        DiagnosticsReport report = service(dataset(
            List.of(realWithRecommendation("real-only", "m-real", 10, T0.plusSeconds(1), "rec-real")),
            List.of(paper("paper-only", "m-paper", 20, T0, "2.00", "2.00", "1.00", null, null, "rec-paper"))
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Recommendation divergence analysis")
            .contains("Paper-only reason breakdown:")
            .contains("Real-only reason breakdown:")
            .contains("Top paper-only examples:")
            .contains("Top real-only examples:")
            .anySatisfy(line -> assertThat(line).contains("Paper-only recommendations").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Real-only recommendations").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("REAL_NOT_ATTEMPTED").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("PAPER_NOT_CREATED").contains("1"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Legacy matching remains official").contains("yes"));
    }

    @Test
    void calculatesStrategyPerformanceSegmentsFromSettledRealBetsOnly() {
        DiagnosticsReport report = service(dataset(
            List.of(
                settledReal("home-win", SelectionSide.HOME, "2.00", "10.00", BetSettlementResult.WIN, "10.00", T0),
                settledReal("draw-loss", SelectionSide.DRAW, "3.50", "10.00", BetSettlementResult.LOSE, "-10.00", T0.plusSeconds(60)),
                settledReal("away-void", SelectionSide.AWAY, "5.50", "10.00", BetSettlementResult.VOID, "0.00", T0.plusSeconds(120)),
                openReal("open-home", SelectionSide.HOME, "2.50", "10.00", T0.plusSeconds(180))
            ),
            List.of()
        ), DiagnosticsLogSummary.empty()).generate(request());

        DiagnosticsStrategyPerformance performance = report.strategyPerformance();
        DiagnosticsStrategyPerformanceSegment baseline = performance.allTime();

        assertThat(baseline.bets()).isEqualTo(4);
        assertThat(baseline.settled()).isEqualTo(3);
        assertThat(baseline.open()).isEqualTo(1);
        assertThat(baseline.wins()).isEqualTo(1);
        assertThat(baseline.losses()).isEqualTo(1);
        assertThat(baseline.voids()).isEqualTo(1);
        assertThat(baseline.averageOdds()).isEqualByComparingTo("3.66666667");
        assertThat(baseline.turnover()).isEqualByComparingTo("30.00");
        assertThat(baseline.netPnl()).isEqualByComparingTo("0.00");
        assertThat(baseline.roi()).isEqualByComparingTo("0.00000000");
        assertThat(baseline.expectedBreakEvenOdds()).isEqualByComparingTo("2.00000000");
        assertThat(baseline.maxDrawdown()).isEqualByComparingTo("10.00");
        assertThat(performance.bySelectionSide()).containsKeys("HOME", "DRAW", "AWAY");
        assertThat(performance.bySelectionSide().get("HOME").settled()).isEqualTo(1);
        assertThat(performance.bySelectionSide().get("DRAW").netPnl()).isEqualByComparingTo("-10.00");
        assertThat(performance.byOddsRange()).containsKeys("2.00-2.49", "3.00-3.99", "5.00+");
    }

    @Test
    void simulatesCandidateFiltersWithoutChangingLegacyMatching() {
        DiagnosticsReport report = service(dataset(
            List.of(
                settledReal("home-win", SelectionSide.HOME, "2.00", "10.00", BetSettlementResult.WIN, "10.00", T0),
                settledReal("draw-loss", SelectionSide.DRAW, "3.50", "10.00", BetSettlementResult.LOSE, "-10.00", T0.plusSeconds(60)),
                settledReal("away-high-loss", SelectionSide.AWAY, "5.50", "10.00", BetSettlementResult.LOSE, "-10.00", T0.plusSeconds(120)),
                settledReal("home-high-win", SelectionSide.HOME, "5.00", "10.00", BetSettlementResult.WIN, "40.00", T0.plusSeconds(180))
            ),
            List.of(paper("paper-legacy", "m-legacy", 10, T0, "2.00", "2.00", "1.00", null, null))
        ), DiagnosticsLogSummary.empty()).generate(request());

        DiagnosticsCandidateFilterResult excludeDraw = filter(report, "EXCLUDE_DRAW");
        DiagnosticsCandidateFilterResult excludeOdds5Plus = filter(report, "EXCLUDE_ODDS_5_PLUS");
        DiagnosticsCandidateFilterResult onlyHome = filter(report, "ONLY_HOME");
        DiagnosticsCandidateFilterResult combined = filter(report, "EXCLUDE_DRAW_AND_ODDS_4_PLUS");

        assertThat(excludeDraw.includedBets()).isEqualTo(3);
        assertThat(excludeDraw.excludedBets()).isEqualTo(1);
        assertThat(excludeDraw.includedNetPnl()).isEqualByComparingTo("40.00");
        assertThat(excludeDraw.deltaPnl()).isEqualByComparingTo("10.00");
        assertThat(excludeDraw.volumeRetentionPct()).isEqualByComparingTo("75.00000000");
        assertThat(excludeOdds5Plus.includedBets()).isEqualTo(2);
        assertThat(excludeOdds5Plus.includedNetPnl()).isEqualByComparingTo("0.00");
        assertThat(onlyHome.includedBets()).isEqualTo(2);
        assertThat(onlyHome.includedNetPnl()).isEqualByComparingTo("50.00");
        assertThat(combined.includedBets()).isEqualTo(1);
        assertThat(combined.includedNetPnl()).isEqualByComparingTo("10.00");
        assertThat(report.candidateFilterSimulation().recommendation().shouldApplyLive()).isFalse();
        assertThat(report.recommendationIdMatchingPreview().enabledAsOfficialMatching()).isFalse();
        assertThat(report.coverage().paperOnly()).isEqualTo(1);
    }

    @Test
    void formatsStrategyPerformanceAndCandidateSimulation() {
        DiagnosticsReport report = service(dataset(
            List.of(settledReal("home-win", SelectionSide.HOME, "2.00", "10.00", BetSettlementResult.WIN, "10.00", T0)),
            List.of()
        ), DiagnosticsLogSummary.empty()).generate(request());

        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Strategy performance diagnostics")
            .contains("Candidate filter simulation")
            .contains("Recommended next experiment")
            .anySatisfy(line -> assertThat(line).contains("should_apply_live").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Matching by recommendation_id").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("Legacy matching remains official").contains("yes"));
    }

    @Test
    void validatesCandidateFilterShadowEvaluationsAgainstSettledRealBets() {
        DiagnosticsDataset dataset = new DiagnosticsDataset(
            List.of(
                settledReal("included-win", SelectionSide.HOME, "2.00", "10.00", BetSettlementResult.WIN, "10.00", T0),
                settledReal("excluded-loss", SelectionSide.DRAW, "3.50", "10.00", BetSettlementResult.LOSE, "-10.00", T0.plusSeconds(60)),
                openReal("open-excluded", SelectionSide.HOME, "5.00", "10.00", T0.plusSeconds(120))
            ),
            List.of(),
            20,
            40,
            Map.of(),
            Map.of(),
            DiagnosticsBetRecommendationsSummary.empty(),
            DiagnosticsPaperRecommendationCoverage.empty(),
            DiagnosticsRecommendationReadiness.empty(),
            List.of(
                shadowEvaluation("rec-included-win", CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS, CandidateFilterDecision.WOULD_PASS, CandidateFilterSource.RECOMMENDATION, T0),
                shadowEvaluation("rec-excluded-loss", CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS, CandidateFilterDecision.WOULD_FILTER, CandidateFilterSource.RECOMMENDATION, T0.plusSeconds(60)),
                shadowEvaluation("rec-open-excluded", CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS, CandidateFilterDecision.WOULD_FILTER, CandidateFilterSource.RECOMMENDATION, T0.plusSeconds(120)),
                shadowEvaluation("rec-included-win", CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS, CandidateFilterDecision.WOULD_PASS, CandidateFilterSource.PAPER, T0)
            )
        );

        DiagnosticsReport report = service(dataset, DiagnosticsLogSummary.empty()).generate(request());
        DiagnosticsCandidateFilterShadowResult result = report.candidateFilterShadowValidation().filters().getFirst();

        assertThat(report.candidateFilterShadowValidation().enabled()).isTrue();
        assertThat(report.candidateFilterShadowValidation().officiallyApplied()).isFalse();
        assertThat(result.filterName()).isEqualTo("EXCLUDE_DRAW_AND_ODDS_4_PLUS");
        assertThat(result.evaluations()).isEqualTo(4);
        assertThat(result.wouldPass()).isEqualTo(2);
        assertThat(result.wouldFilter()).isEqualTo(2);
        assertThat(result.realBetsObserved()).isEqualTo(2);
        assertThat(result.paperTradesObserved()).isEqualTo(1);
        assertThat(result.settledIncluded()).isEqualTo(1);
        assertThat(result.settledExcluded()).isEqualTo(1);
        assertThat(result.baselinePnl()).isEqualByComparingTo("0.00");
        assertThat(result.shadowIncludedPnl()).isEqualByComparingTo("10.00");
        assertThat(result.shadowExcludedPnl()).isEqualByComparingTo("-10.00");
        assertThat(result.deltaPnl()).isEqualByComparingTo("10.00");
        assertThat(result.status()).isEqualTo(DiagnosticsCandidateFilterStatus.INSUFFICIENT_SAMPLE);
        assertThat(result.shouldApplyLive()).isFalse();

        assertThat(new DiagnosticsFormatter().format(report))
            .contains("Candidate filter shadow validation")
            .anySatisfy(line -> assertThat(line).contains("Officially applied").contains("no"))
            .anySatisfy(line -> assertThat(line).contains("should_apply_live").contains("no"));
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

    private static CandidateFilterEvaluation shadowEvaluation(
        String recommendationId,
        CandidateFilterName filterName,
        CandidateFilterDecision decision,
        CandidateFilterSource source,
        Instant evaluatedAt
    ) {
        return new CandidateFilterEvaluation(
            recommendationId + "-" + filterName.name() + "-" + source.name(),
            recommendationId,
            BetRecommendation.canonicalKey("betfair", "m-" + recommendationId.substring(4), 1L, SelectionSide.HOME, "value-football"),
            filterName,
            decision,
            decision == CandidateFilterDecision.WOULD_FILTER
                ? CandidateFilterDecisionReason.SELECTION_SIDE_DRAW
                : CandidateFilterDecisionReason.PASSED,
            SelectionSide.HOME,
            new BigDecimal("2.00"),
            "value-football",
            source,
            evaluatedAt,
            evaluatedAt,
            evaluatedAt,
            1
        );
    }

    private static DiagnosticsLogSummary logsWithEvents(DiagnosticsLogEvent... events) {
        return new DiagnosticsLogSummary(Map.of(), Map.of(), List.of(), 0, 0, List.of(), List.of(events));
    }

    private static DiagnosticsLogEvent logEvent(
        String eventName,
        String recommendationId,
        String marketId,
        long selectionId,
        String reason,
        Instant timestamp
    ) {
        return new DiagnosticsLogEvent(
            timestamp,
            eventName,
            recommendationId,
            BetRecommendation.canonicalKey("betfair", marketId, selectionId, SelectionSide.DRAW, "value-football"),
            "betfair",
            marketId,
            selectionId,
            "DRAW",
            "value-football",
            reason,
            reason,
            "Event " + marketId,
            "Runner " + selectionId
        );
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

    private static RealBetDiagnosticRow realWithRecommendation(
        String id,
        String marketId,
        long selectionId,
        Instant createdAt,
        String recommendationId
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
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            BetIntentStage.EXECUTED,
            null,
            null,
            "external-" + id,
            createdAt,
            null,
            createdAt.plusSeconds(120),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(99),
            BigDecimal.ONE,
            createdAt,
            "eval-" + id,
            recommendationId,
            createdAt,
            BigDecimal.valueOf(2),
            createdAt,
            createdAt.plusMillis(100),
            null,
            null,
            BigDecimal.valueOf(2),
            null,
            BigDecimal.ONE,
            null,
            null,
            BetExecutionStatus.UNMATCHED
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

    private static RealBetDiagnosticRow settledReal(
        String id,
        SelectionSide selectionSide,
        String odds,
        String stake,
        BetSettlementResult result,
        String pnl,
        Instant createdAt
    ) {
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            "m-" + id,
            Math.abs(id.hashCode()) + 1L,
            "Event " + id,
            "Match Odds",
            "Runner " + id,
            selectionSide,
            "League",
            "value-football",
            decimal(odds),
            decimal(stake),
            BetIntentStage.SETTLED,
            result,
            decimal(pnl),
            "external-" + id,
            createdAt,
            createdAt.plusSeconds(3600),
            createdAt.plusSeconds(120),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(90),
            decimal(stake),
            createdAt,
            "eval-" + id,
            "rec-" + id,
            createdAt,
            decimal(odds),
            createdAt,
            createdAt.plusMillis(100),
            null,
            result == BetSettlementResult.WIN ? createdAt.plusMillis(200) : null,
            decimal(odds),
            result == BetSettlementResult.WIN ? decimal(odds) : null,
            decimal(stake),
            result == BetSettlementResult.VOID ? BigDecimal.ZERO : decimal(stake),
            BigDecimal.ZERO,
            result == BetSettlementResult.WIN ? BetExecutionStatus.FULLY_MATCHED : BetExecutionStatus.UNMATCHED
        );
    }

    private static RealBetDiagnosticRow openReal(
        String id,
        SelectionSide selectionSide,
        String odds,
        String stake,
        Instant createdAt
    ) {
        return new RealBetDiagnosticRow(
            id,
            "betfair",
            "m-" + id,
            Math.abs(id.hashCode()) + 1L,
            "Event " + id,
            "Match Odds",
            "Runner " + id,
            selectionSide,
            "League",
            "value-football",
            decimal(odds),
            decimal(stake),
            BetIntentStage.EXECUTED,
            null,
            null,
            "external-" + id,
            createdAt,
            null,
            createdAt.plusSeconds(120),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(90),
            decimal(stake),
            createdAt
        );
    }

    private static DiagnosticsCandidateFilterResult filter(DiagnosticsReport report, String filterName) {
        return report.candidateFilterSimulation().results().stream()
            .filter(result -> result.filterName().equals(filterName))
            .findFirst()
            .orElseThrow();
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
        return paper(id, marketId, selectionId, recommendedAt, requestedOdds, executionOdds, stake, result, pnl, null);
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
        String pnl,
        String recommendationId
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
            true,
            recommendationId
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
