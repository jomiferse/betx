package com.betx.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiagnosticsJsonExporter {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public void export(DiagnosticsReport report, Path exportPath) {
        try {
            Path parent = exportPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(exportPath.toFile(), payload(report));
        } catch (IOException exc) {
            throw new UncheckedIOException("Could not write diagnostics JSON export: " + exportPath, exc);
        }
    }

    private Map<String, Object> payload(DiagnosticsReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", instant(report.generatedAt()));
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("from", instant(report.period().from()));
        period.put("to", instant(report.period().to()));
        period.put("label", report.period().label());
        payload.put("period", period);
        payload.put("coverage", report.coverage());
        payload.put("betRecommendations", report.betRecommendations());
        payload.put("recommendationReadiness", recommendationReadiness(report.recommendationReadiness()));
        payload.put("recommendationIdMatchingPreview", recommendationIdMatchingPreview(report.recommendationIdMatchingPreview()));
        payload.put("paperRecommendationCoverage", report.paperRecommendationCoverage());
        payload.put("decisionFunnel", report.decisionFunnel());
        payload.put("executionMetrics", execution(report.executionMetrics()));
        payload.put("logEventCoverage", report.logEventCoverage());
        payload.put("persistedExecutionCoverage", report.persistedExecutionCoverage());
        payload.put("placeOrdersResponseDuration", placeOrdersResponseDuration(report.placeOrdersResponseDuration()));
        payload.put("prospectiveRealBettingCohort", report.prospectiveRealBettingCohort());
        payload.put("executionDataCoverage", report.executionDataCoverage());
        payload.put("topSkippedMarkets", report.topSkippedMarkets());
        payload.put("matchingGaps", report.matchingGaps());
        payload.put("paperVsRealMetrics", report.paperVsRealMetrics());
        payload.put("integrityFindings", report.integrityFindings());
        payload.put("limitations", report.limitations());
        payload.put("topFindings", report.topFindings());
        payload.put("matchedPairs", report.matchedPairs().stream().map(this::match).toList());
        return payload;
    }

    private Map<String, Object> recommendationIdMatchingPreview(DiagnosticsRecommendationIdMatchingPreview preview) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("previewAvailable", preview.previewAvailable());
        value.put("enabledAsOfficialMatching", preview.enabledAsOfficialMatching());
        value.put("allTime", recommendationIdMatchingScope(preview.allTime()));
        value.put("post25", recommendationIdMatchingScope(preview.post25()));
        return value;
    }

    private Map<String, Object> recommendationIdMatchingScope(DiagnosticsRecommendationIdMatchingScope scope) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("scope", scope.scope());
        value.put("cutoff", instant(scope.cutoff()));
        value.put("paperTradesTotal", scope.paperTradesTotal());
        value.put("paperTradesWithRecommendationId", scope.paperTradesWithRecommendationId());
        value.put("paperTradesEligible", scope.paperTradesEligible());
        value.put("realBetsTotal", scope.realBetsTotal());
        value.put("realBetsWithRecommendationId", scope.realBetsWithRecommendationId());
        value.put("realBetsEligible", scope.realBetsEligible());
        value.put("recommendationsWithBothPaperAndReal", scope.recommendationsWithBothPaperAndReal());
        value.put("recommendationsWithPaperOnly", scope.recommendationsWithPaperOnly());
        value.put("recommendationsWithRealOnly", scope.recommendationsWithRealOnly());
        value.put("recommendationsWithNeither", scope.recommendationsWithNeither());
        value.put("recommendationIdPairs", scope.recommendationIdPairs());
        value.put("recommendationIdPaperOnly", scope.recommendationIdPaperOnly());
        value.put("recommendationIdRealOnly", scope.recommendationIdRealOnly());
        value.put("recommendationIdAmbiguous", scope.recommendationIdAmbiguous());
        value.put("ambiguousManyPaperToOneReal", scope.ambiguousManyPaperToOneReal());
        value.put("ambiguousOnePaperToManyReal", scope.ambiguousOnePaperToManyReal());
        value.put("ambiguousManyToMany", scope.ambiguousManyToMany());
        value.put("legacyComparison", scope.legacyComparison());
        return value;
    }

    private Map<String, Object> recommendationReadiness(DiagnosticsRecommendationReadiness readiness) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("paperConsumesBetRecommendation", readiness.paperTradesWithRecommendationId() > 0);
        value.put("realConsumesBetRecommendation", readiness.realBetsWithRecommendationId() > 0);
        value.put("matchingByRecommendationId", false);
        value.put("recommendationIdMatchingOfficial", false);
        value.put("legacyMatchingRemainsOfficial", true);
        value.put("totalCanonicalRecommendations", readiness.totalCanonicalRecommendations());
        value.put("activeRecommendations", readiness.activeRecommendations());
        value.put("coveredRecommendations", readiness.coveredRecommendations());
        value.put("expiredRecommendations", readiness.expiredRecommendations());
        value.put("recommendationsWithPaperTrades", readiness.recommendationsWithPaperTrades());
        value.put("recommendationsWithoutPaperTrades", readiness.recommendationsWithoutPaperTrades());
        value.put("recommendationsWithRealEquivalentBet", readiness.recommendationsWithRealEquivalentBet());
        value.put("recommendationsWithoutRealEquivalentBet", readiness.recommendationsWithoutRealEquivalentBet());
        value.put("recommendationsWithBothPaperAndRealEquivalent", readiness.recommendationsWithBothPaperAndRealEquivalent());
        value.put("recommendationsWithPaperOnly", readiness.recommendationsWithPaperOnly());
        value.put("recommendationsWithRealOnly", readiness.recommendationsWithRealOnly());
        value.put("recommendationsWithNeitherPaperNorReal", readiness.recommendationsWithNeitherPaperNorReal());
        value.put("paperTradesWithRecommendationId", readiness.paperTradesWithRecommendationId());
        value.put("paperTradesMissingRecommendationIdPost23", readiness.paperTradesMissingRecommendationIdPost23());
        value.put("brokenPaperRecommendationJoins", readiness.brokenPaperRecommendationJoins());
        value.put("realBetsWithRecommendationId", readiness.realBetsWithRecommendationId());
        value.put("realBetsMissingRecommendationId", readiness.realBetsMissingRecommendationId());
        value.put("realBetsTotal", readiness.realBetsTotal());
        value.put("post25RealBets", readiness.post25RealBets());
        value.put("post25RealBetsWithRecommendationId", readiness.post25RealBetsWithRecommendationId());
        value.put("post25RealBetsWithoutRecommendationId", readiness.post25RealBetsWithoutRecommendationId());
        value.put("realBetsWithRecommendationIdButMissingBetRecommendation", readiness.realBetsWithRecommendationIdButMissingBetRecommendation());
        value.put("realBetsLinkedToCanonicalRecommendation", readiness.realBetsLinkedToCanonicalRecommendation());
        value.put("realBetsLinkedToActiveRecommendations", readiness.realBetsLinkedToActiveRecommendations());
        value.put("realBetsLinkedToCoveredRecommendations", readiness.realBetsLinkedToCoveredRecommendations());
        value.put("realBetsLinkedToExpiredRecommendations", readiness.realBetsLinkedToExpiredRecommendations());
        value.put("realEquivalentCoverageSource", readiness.realEquivalentCoverageSource());
        value.put("readyForRealConsumption", readiness.readyForRealConsumption());
        value.put("readyForRecommendationIdMatching", readiness.readyForRecommendationIdMatching());
        value.put("readinessStatus", readiness.readinessStatus());
        value.put("readinessReasons", readiness.readinessReasons());
        return value;
    }

    private Map<String, Object> execution(DiagnosticsExecutionMetrics metrics) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("ordersSubmitted", metrics.ordersSubmitted());
        value.put("fullyMatched", metrics.fullyMatched());
        value.put("partiallyMatched", metrics.partiallyMatched());
        value.put("unmatched", metrics.unmatched());
        value.put("rejected", metrics.rejected());
        value.put("cancelled", metrics.cancelled());
        value.put("averageExecutionLatencyMs", millis(metrics.averageExecutionLatency()));
        value.put("medianExecutionLatencyMs", millis(metrics.medianExecutionLatency()));
        value.put("p95ExecutionLatencyMs", millis(metrics.p95ExecutionLatency()));
        value.put("latencyProvenance", metrics.latencyProvenance());
        value.put("averageRealRecordedVsPaperOddsDifference", metrics.averageRealRecordedVsPaperOddsDifference());
        value.put("oddsProvenance", metrics.oddsProvenance());
        value.put("missingRecordedOdds", metrics.missingRecordedOdds());
        value.put("missingExchangeOrderId", metrics.missingExchangeOrderId());
        return value;
    }

    private Map<String, Object> placeOrdersResponseDuration(DiagnosticsPlaceOrdersResponseDuration duration) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("observations", duration.observations());
        value.put("averageMs", millis(duration.average()));
        value.put("medianMs", millis(duration.median()));
        value.put("p95Ms", millis(duration.p95()));
        value.put("minimumMs", millis(duration.minimum()));
        value.put("maximumMs", millis(duration.maximum()));
        value.put("orderResponseBeforeSubmission", duration.responseBeforeSubmission());
        value.put("missingOrderResponse", duration.missingOrderResponse());
        value.put("slowPlaceOrderResponse", duration.slowResponses());
        value.put("provenance", duration.provenance());
        return value;
    }

    private Map<String, Object> match(DiagnosticsMatch match) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("matchStatus", match.matchStatus());
        value.put("matchProvenance", match.matchProvenance());
        value.put("matchGapReason", match.matchGapReason());
        value.put("candidateCount", match.candidateCount());
        value.put("nearestCandidateTimeDifferenceMs", millis(match.nearestCandidateTimeDifference()));
        value.put("recommendationId", match.recommendationId());
        value.put("evaluationId", match.evaluationId());
        value.put("eventName", match.eventName());
        value.put("marketId", match.marketId());
        value.put("selectionId", match.selectionId());
        value.put("runnerName", match.runnerName());
        value.put("selectionSide", match.selectionSide());
        value.put("competitionName", match.competitionName());
        value.put("strategyName", match.strategyName());
        value.put("recommendationTimestamp", instant(match.recommendationTimestamp()));
        value.put("paperExecutionTimestamp", instant(match.paperExecutionTimestamp()));
        value.put("realRecordedTimestamp", instant(match.realRecordedTimestamp()));
        value.put("recommendedOdds", match.recommendedOdds());
        value.put("recommendedAt", instant(match.recommendedAt()));
        value.put("exactRecommendedOdds", match.exactRecommendedOdds());
        value.put("orderSubmittedAt", instant(match.orderSubmittedAt()));
        value.put("orderResponseAt", instant(match.orderResponseAt()));
        value.put("orderAcceptedAt", instant(match.orderAcceptedAt()));
        value.put("executedAt", instant(match.executedAt()));
        value.put("requestedOdds", match.requestedOdds());
        value.put("averageExecutedOdds", match.averageExecutedOdds());
        value.put("requestedStake", match.requestedStake());
        value.put("matchedStake", match.matchedStake());
        value.put("remainingStake", match.remainingStake());
        value.put("executionStatus", match.executionStatus());
        value.put("paperOdds", match.paperOdds());
        value.put("realRecordedOdds", match.realRecordedOdds());
        value.put("realOddsSource", match.realOddsSource());
        value.put("closingOdds", match.closingOdds());
        value.put("paperStake", match.paperStake());
        value.put("realStake", match.realStake());
        value.put("paperResult", match.paperResult());
        value.put("realResult", match.realResult());
        value.put("paperPnl", match.paperPnl());
        value.put("realPnl", match.realPnl());
        value.put("executionPnlDifference", match.executionPnlDifference());
        value.put("paperPnlPerUnitStake", match.paperPnlPerUnitStake());
        value.put("realPnlPerUnitStake", match.realPnlPerUnitStake());
        value.put("normalizedExecutionDifference", match.normalizedExecutionDifference());
        return value;
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Long millis(Duration value) {
        return value == null ? null : value.toMillis();
    }
}
