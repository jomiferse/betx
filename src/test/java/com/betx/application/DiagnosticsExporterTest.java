package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.MatchStatus;
import com.betx.application.DiagnosticsModel.RealOddsSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesValidJsonAndCsvWithoutSecretFields() throws Exception {
        DiagnosticsReport report = report();
        Path json = tempDir.resolve("diagnostics.json");
        Path csv = tempDir.resolve("matched.csv");

        new DiagnosticsJsonExporter().export(report, json);
        new DiagnosticsCsvExporter().export(report, csv);

        String jsonText = Files.readString(json);
        assertThat(jsonText)
            .contains("\"generatedAt\"")
            .contains("\"matchedPairs\"")
            .contains("\"recommendationReadiness\"")
            .contains("\"readyForRealConsumption\"")
            .contains("\"readyForRecommendationIdMatching\"")
            .contains("\"paperConsumesBetRecommendation\" : true")
            .contains("\"realConsumesBetRecommendation\" : true")
            .contains("\"matchingByRecommendationId\" : false")
            .contains("\"recommendationIdMatchingOfficial\" : false")
            .contains("\"legacyMatchingRemainsOfficial\" : true")
            .contains("\"recommendationIdMatchingPreview\"")
            .contains("\"previewAvailable\" : true")
            .contains("\"enabledAsOfficialMatching\" : false")
            .contains("\"recommendationIdPairs\" : 1")
            .contains("\"legacyComparison\"")
            .contains("\"recommendationDivergenceAnalysis\"")
            .contains("\"paperOnlyReasonBreakdown\"")
            .contains("\"realOnlyReasonBreakdown\"")
            .contains("\"reason\" : \"REAL_NOT_ATTEMPTED\"")
            .contains("\"evidence\"")
            .contains("\"source\" : \"DIAGNOSTICS\"")
            .contains("\"strategyPerformance\"")
            .contains("\"candidateFilterSimulation\"")
            .contains("\"candidateFilterShadowValidation\"")
            .contains("\"officiallyApplied\" : false")
            .contains("\"bySelectionSide\"")
            .contains("\"filterName\" : \"EXCLUDE_DRAW\"")
            .contains("\"shouldApplyLive\" : false")
            .contains("\"paperTradesLinkedToExpiredRecommendations\"")
            .doesNotContain("token")
            .doesNotContain("password")
            .doesNotContain("session");
        assertThat(Files.readAllLines(csv))
            .hasSize(2)
            .first()
            .asString()
            .contains("match_status,match_provenance,match_gap_reason")
            .contains("evaluation_id")
            .contains("order_submitted_at")
            .contains("average_executed_odds");
    }

    @Test
    void writesStakeSizingShadowDiagnosticsToJson() throws Exception {
        DiagnosticsReport base = report();
        DiagnosticsReport report = new DiagnosticsReport(
            base.generatedAt(),
            base.period(),
            base.coverage(),
            base.decisionFunnel(),
            base.executionMetrics(),
            base.paperVsRealMetrics(),
            base.integrityFindings(),
            base.limitations(),
            base.topFindings(),
            base.matchedPairs(),
            base.matchingGaps(),
            base.executionDataCoverage(),
            base.logEventCoverage(),
            base.persistedExecutionCoverage(),
            base.placeOrdersResponseDuration(),
            base.prospectiveRealBettingCohort(),
            base.topSkippedMarkets(),
            base.betRecommendations(),
            base.paperRecommendationCoverage(),
            base.recommendationReadiness(),
            base.recommendationIdMatchingPreview(),
            base.recommendationDivergenceAnalysis(),
            base.strategyPerformance(),
            base.candidateFilterSimulation(),
            base.candidateFilterShadowValidation(),
            new DiagnosticsStakeSizingShadowDiagnostics(
                true,
                false,
                false,
                new DiagnosticsStakeSizingSummary(
                    1,
                    1,
                    java.util.Set.of("RISK_ADJUSTED"),
                    java.util.Set.of("CONSERVATIVE"),
                    java.util.Set.of("SHADOW"),
                    3,
                    Instant.parse("2026-06-01T10:00:00Z"),
                    Instant.parse("2026-06-01T10:05:00Z"),
                    java.time.Duration.ofMinutes(5),
                    0,
                    0,
                    0
                ),
                List.of(),
                null
            ),
            new DiagnosticsStakeSizingScenarioSimulation(
                true,
                false,
                false,
                List.of(new DiagnosticsStakeSizingScenario(
                    "SCENARIO_BASE_10_MIN_0_10",
                    new BigDecimal("10.00"),
                    new BigDecimal("0.10"),
                    new BigDecimal("100.00"),
                    List.of(new DiagnosticsStakeSizingScenarioPolicyResult(
                        "SCENARIO_BASE_10_MIN_0_10",
                        "RISK_ADJUSTED",
                        "CONSERVATIVE",
                        new BigDecimal("10.00"),
                        new BigDecimal("0.10"),
                        new BigDecimal("100.00"),
                        1,
                        1,
                        1,
                        0,
                        1,
                        0,
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00"),
                        new BigDecimal("2.00000000"),
                        new BigDecimal("0.94"),
                        new BigDecimal("1.88"),
                        new BigDecimal("2.00000000"),
                        new BigDecimal("-0.12"),
                        BigDecimal.ZERO,
                        new BigDecimal("0.94000000"),
                        new BigDecimal("0.94000000"),
                        new BigDecimal("0.94"),
                        new BigDecimal("0.94"),
                        new BigDecimal("0.94000000"),
                        new BigDecimal("0.94"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        0,
                        DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE,
                        "not enough sample for live staking decision",
                        DiagnosticsStakeSizingRankingEligibility.INSUFFICIENT_SAMPLE,
                        true,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        true,
                        false,
                        false
                    ))
                )),
                new DiagnosticsStakeSizingScenarioRanking(
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    null,
                    "not enough sample for live staking decision",
                    false
                ),
                new DiagnosticsStakeSizingRankingEligibilitySummary(0, 1, 0, 0, 0, 0, 0),
                new DiagnosticsStakeSizingScenarioRanking(
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    null,
                    "not enough sample for live staking decision",
                    false
                ),
                List.of(),
                new DiagnosticsStakeSizingRankingSummary(1, 1, 0, 0, 0, 0, 0, 0, 0, 1),
                new DiagnosticsStakeSizingScenarioRanking(
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    "SCENARIO_BASE_10_MIN_0_10/RISK_ADJUSTED/CONSERVATIVE",
                    null,
                    "not enough sample for live staking decision",
                    false
                ),
                new DiagnosticsStakeSizingLiveEligibleRankings(false, "NO_LIVE_ELIGIBLE_RESULTS_INSUFFICIENT_SAMPLE", DiagnosticsStakeSizingScenarioRanking.empty()),
                List.of(),
                List.of()
            )
        );
        Path json = tempDir.resolve("diagnostics.json");

        new DiagnosticsJsonExporter().export(report, json);

        assertThat(Files.readString(json))
            .contains("\"stakeSizingShadowDiagnostics\"")
            .contains("\"enabled\" : true")
            .contains("\"officiallyApplied\" : false")
            .contains("\"shouldApplyLive\" : false")
            .contains("\"decisions\" : 1")
            .contains("\"policyResults\"")
            .contains("\"stakeSizingScenarioSimulation\"")
            .contains("\"rankingSummary\"")
            .contains("\"watchRankings\"")
            .contains("\"liveEligibleRankings\"")
            .contains("\"excludedFromUsefulRankings\"")
            .contains("\"watchCandidates\"")
            .contains("\"scenarioName\" : \"SCENARIO_BASE_10_MIN_0_10\"")
            .contains("\"policyName\" : \"RISK_ADJUSTED\"")
            .contains("\"rankingEligibility\" : \"INSUFFICIENT_SAMPLE\"")
            .contains("\"eligibleForUsefulRanking\" : true")
            .contains("\"watchCandidate\" : true")
            .contains("\"eligibleForLive\" : false")
            .contains("\"shouldApplyLive\" : false");
    }

    @Test
    void writesStakeSizingLiveGateDiagnosticsToJson() throws Exception {
        DiagnosticsReport base = report();
        DiagnosticsReport report = new DiagnosticsReport(
            base.generatedAt(),
            base.period(),
            base.coverage(),
            base.decisionFunnel(),
            base.executionMetrics(),
            base.paperVsRealMetrics(),
            base.integrityFindings(),
            base.limitations(),
            base.topFindings(),
            base.matchedPairs(),
            base.matchingGaps(),
            base.executionDataCoverage(),
            base.logEventCoverage(),
            base.persistedExecutionCoverage(),
            base.placeOrdersResponseDuration(),
            base.prospectiveRealBettingCohort(),
            base.topSkippedMarkets(),
            base.betRecommendations(),
            base.paperRecommendationCoverage(),
            base.recommendationReadiness(),
            base.recommendationIdMatchingPreview(),
            base.recommendationDivergenceAnalysis(),
            base.strategyPerformance(),
            base.candidateFilterSimulation(),
            base.candidateFilterShadowValidation(),
            base.stakeSizingShadowDiagnostics(),
            base.stakeSizingScenarioSimulation(),
            new DiagnosticsStakeSizingLiveGateDiagnostics(
                true,
                false,
                false,
                true,
                "RISK_ADJUSTED",
                "CONSERVATIVE",
                "FAIL",
                false,
                false,
                false,
                false,
                "FIXED_FALLBACK",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("3.75"),
                "SCENARIO_BASE_5_MIN_1/RISK_ADJUSTED/CONSERVATIVE",
                new DiagnosticsStakeSizingLiveGateSample(51, 100),
                new DiagnosticsStakeSizingLiveGateHealth(0, 0, 0, true),
                new DiagnosticsStakeSizingLiveGateRisk(BigDecimal.ZERO, new BigDecimal("25.00")),
                new DiagnosticsStakeSizingLiveGateBudget(new BigDecimal("25.00"), new BigDecimal("50.00"), new BigDecimal("5.00"), true),
                new DiagnosticsStakeSizingLiveGateExposure(10, true),
                new DiagnosticsStakeSizingLiveGateKillSwitch(false, List.of()),
                false,
                List.of("LIVE_STAKING_DISABLED", "INSUFFICIENT_SAMPLE_FOR_LIVE_STAKING"),
                List.of(),
                new DiagnosticsStakeSizingLiveGateDryRun(
                    true,
                    2,
                    1,
                    null,
                    null,
                    List.of(),
                    null,
                    new BigDecimal("1.00"),
                    new BigDecimal("1.00"),
                    0,
                    0
                )
            )
        );
        Path json = tempDir.resolve("diagnostics.json");

        new DiagnosticsJsonExporter().export(report, json);

        assertThat(Files.readString(json))
            .contains("\"stakeSizingLiveGateDiagnostics\"")
            .contains("\"gateStatus\" : \"FAIL\"")
            .contains("\"candidatePolicy\" : \"RISK_ADJUSTED\"")
            .contains("\"candidateRiskProfile\" : \"CONSERVATIVE\"")
            .contains("\"reasons\"")
            .contains("\"sample\"")
            .contains("\"realSettledJoined\" : 51")
            .contains("\"minSettledJoinedRequired\" : 100")
            .contains("\"shouldApplyLive\" : false")
            .contains("\"officiallyApplied\" : false")
            .contains("\"dryRun\"")
            .contains("\"evaluationsTotal\" : 2")
            .contains("\"failedTotal\" : 1")
            .contains("\"liveAppliedEvents\" : 0")
            .contains("\"orderStakeChangedEvents\" : 0");
    }

    private static DiagnosticsReport report() {
        DiagnosticsMatch match = new DiagnosticsMatch(
            MatchStatus.MATCHED,
            "A v B",
            "m1",
            10L,
            "Draw",
            "DRAW",
            "League",
            "value-football",
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T10:00:01Z"),
            Instant.parse("2026-06-01T10:00:02Z"),
            new BigDecimal("2.90"),
            new BigDecimal("2.90"),
            new BigDecimal("3.00"),
            RealOddsSource.BET_INTENT,
            new BigDecimal("2.80"),
            new BigDecimal("5.00"),
            new BigDecimal("10.00"),
            "WIN",
            "WIN",
            new BigDecimal("9.50"),
            new BigDecimal("20.00"),
            new BigDecimal("10.50"),
            new BigDecimal("1.90000000"),
            new BigDecimal("2.00000000"),
            new BigDecimal("0.10000000")
        );
        DiagnosticsReport base = new DiagnosticsReport(
            Instant.parse("2026-06-02T00:00:00Z"),
            new DiagnosticsPeriod(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z")),
            new DiagnosticsCoverage(1, 1, 1, 0, 0, 0),
            new DiagnosticsDecisionFunnel(1, 1, 1, 0, 0, 0, 1, 1, 0, 1, java.util.Map.of()),
            new DiagnosticsExecutionMetrics(1, 1, 0, 0, 0, 0, null, null, null, DiagnosticsDataProvenance.UNAVAILABLE, null, DiagnosticsDataProvenance.UNAVAILABLE, 0, 0),
            new DiagnosticsPaperVsRealMetrics(1, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null, 0, DiagnosticsDataProvenance.APPROXIMATED, DiagnosticsDataProvenance.SQLITE_EXACT),
            List.of(),
            List.of(),
            List.of("Matched paper-real pairs: 1 observations."),
            List.of(match)
        );
        return new DiagnosticsReport(
            base.generatedAt(),
            base.period(),
            base.coverage(),
            base.decisionFunnel(),
            base.executionMetrics(),
            base.paperVsRealMetrics(),
            base.integrityFindings(),
            base.limitations(),
            base.topFindings(),
            base.matchedPairs(),
            base.matchingGaps(),
            base.executionDataCoverage(),
            base.logEventCoverage(),
            base.persistedExecutionCoverage(),
            base.placeOrdersResponseDuration(),
            base.prospectiveRealBettingCohort(),
            base.topSkippedMarkets(),
            base.betRecommendations(),
            base.paperRecommendationCoverage(),
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
                "YES",
                "NO",
                "RECOMMENDATION_ID_MATCHING_CANDIDATE",
                List.of("recommendation_id matching is not enabled as official matching yet.")
            ),
            new DiagnosticsRecommendationIdMatchingPreview(
                true,
                false,
                new DiagnosticsRecommendationIdMatchingScope(
                    "all-time",
                    null,
                    1,
                    1,
                    1,
                    1,
                    1,
                    1,
                    1,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    new DiagnosticsRecommendationLegacyComparison(1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
                ),
                DiagnosticsRecommendationIdMatchingScope.empty()
            ),
            new DiagnosticsRecommendationDivergenceAnalysis(
                1,
                0,
                0,
                java.util.Map.of(DiagnosticsRecommendationDivergenceReason.REAL_NOT_ATTEMPTED, 1L),
                java.util.Map.of(),
                0,
                0,
                List.of(new DiagnosticsRecommendationDivergenceExample(
                    "rec-paper-only",
                    "betfair|m2|20|DRAW|value-football",
                    "A v B",
                    "Draw",
                    "m2",
                    20L,
                    "DRAW",
                    "value-football",
                    Instant.parse("2026-06-01T10:00:00Z"),
                    Instant.parse("2026-06-01T10:00:00Z"),
                    1,
                    0,
                    "PAPER_ONLY",
                    DiagnosticsRecommendationDivergenceReason.REAL_NOT_ATTEMPTED,
                    List.of(new DiagnosticsRecommendationDivergenceEvidence(
                        "diagnostics.divergence",
                        Instant.parse("2026-06-01T10:00:00Z"),
                        "No real-side evidence was found for this recommendation.",
                        DiagnosticsModel.DiagnosticsDataProvenance.DIAGNOSTICS,
                        "rec-paper-only"
                    ))
                )),
                List.of()
            ),
            new DiagnosticsStrategyPerformance(
                new DiagnosticsStrategyPerformanceSegment(
                    "all-time",
                    2,
                    2,
                    0,
                    1,
                    1,
                    0,
                    0,
                    new BigDecimal("0.50000000"),
                    new BigDecimal("2.50000000"),
                    new BigDecimal("10.00"),
                    new BigDecimal("20.00"),
                    null,
                    null,
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(8),
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ONE.setScale(8),
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("2.00000000")
                ),
                List.of(),
                java.util.Map.of("DRAW", DiagnosticsStrategyPerformanceSegment.empty("DRAW")),
                java.util.Map.of("2.00-2.49", DiagnosticsStrategyPerformanceSegment.empty("2.00-2.49")),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                List.of("edge/confidence/liquidity unavailable")
            ),
            new DiagnosticsCandidateFilterSimulation(
                DiagnosticsStrategyPerformanceSegment.empty("baseline"),
                List.of(new DiagnosticsCandidateFilterResult(
                    "EXCLUDE_DRAW",
                    "all-time",
                    2,
                    1,
                    1,
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ONE.setScale(8),
                    new BigDecimal("2.00000000"),
                    new BigDecimal("10.00"),
                    BigDecimal.ONE.setScale(8),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(8),
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ONE.setScale(8),
                    new BigDecimal("50.00000000"),
                    DiagnosticsCandidateFilterStatus.INSUFFICIENT_SAMPLE,
                    "Sample too small for statistical confidence. Observations: 1.",
                    "sample size too small"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DiagnosticsStrategyExperimentRecommendation(
                    "EXCLUDE_DRAW",
                    "diagnostics-only",
                    "sample",
                    "risk",
                    false
                )
            )
        );
    }
}
