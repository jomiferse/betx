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
            )
        );
    }
}
