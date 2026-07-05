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
        payload.put("recommendationDivergenceAnalysis", recommendationDivergenceAnalysis(report.recommendationDivergenceAnalysis()));
        payload.put("strategyPerformance", report.strategyPerformance());
        payload.put("candidateFilterSimulation", report.candidateFilterSimulation());
        payload.put("candidateFilterShadowValidation", candidateFilterShadowValidation(report.candidateFilterShadowValidation()));
        payload.put("stakeSizingShadowDiagnostics", stakeSizingShadowDiagnostics(report.stakeSizingShadowDiagnostics()));
        payload.put("stakeSizingScenarioSimulation", stakeSizingScenarioSimulation(report.stakeSizingScenarioSimulation()));
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

    private Map<String, Object> recommendationDivergenceAnalysis(DiagnosticsRecommendationDivergenceAnalysis analysis) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("paperOnlyRecommendations", analysis.paperOnlyRecommendations());
        value.put("realOnlyRecommendations", analysis.realOnlyRecommendations());
        value.put("ambiguousRecommendations", analysis.ambiguousRecommendations());
        value.put("paperOnlyReasonBreakdown", analysis.paperOnlyReasonBreakdown());
        value.put("realOnlyReasonBreakdown", analysis.realOnlyReasonBreakdown());
        value.put("unknownPaperOnly", analysis.unknownPaperOnly());
        value.put("unknownRealOnly", analysis.unknownRealOnly());
        value.put("topPaperOnlyExamples", analysis.topPaperOnlyExamples().stream().map(this::recommendationDivergenceExample).toList());
        value.put("topRealOnlyExamples", analysis.topRealOnlyExamples().stream().map(this::recommendationDivergenceExample).toList());
        return value;
    }

    private Map<String, Object> recommendationDivergenceExample(DiagnosticsRecommendationDivergenceExample example) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("recommendationId", example.recommendationId());
        value.put("canonicalKey", example.canonicalKey());
        value.put("eventName", example.eventName());
        value.put("runnerName", example.runnerName());
        value.put("marketId", example.marketId());
        value.put("selectionId", example.selectionId());
        value.put("selectionSide", example.selectionSide());
        value.put("strategyName", example.strategyName());
        value.put("firstSeenAt", instant(example.firstSeenAt()));
        value.put("lastSeenAt", instant(example.lastSeenAt()));
        value.put("paperCount", example.paperCount());
        value.put("realCount", example.realCount());
        value.put("classification", example.classification());
        value.put("reason", example.reason());
        value.put("evidence", example.evidence().stream().map(this::recommendationDivergenceEvidence).toList());
        return value;
    }

    private Map<String, Object> recommendationDivergenceEvidence(DiagnosticsRecommendationDivergenceEvidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("eventName", evidence.eventName());
        value.put("timestamp", instant(evidence.timestamp()));
        value.put("message", evidence.message());
        value.put("source", evidence.source());
        value.put("recommendationId", evidence.recommendationId());
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

    private Map<String, Object> candidateFilterShadowValidation(DiagnosticsCandidateFilterShadowValidation validation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enabled", validation.enabled());
        value.put("officiallyApplied", validation.officiallyApplied());
        value.put("post32Cutoff", instant(validation.post32Cutoff()));
        value.put("shouldApplyLive", validation.shouldApplyLive());
        value.put("filters", validation.filters().stream().map(this::candidateFilterShadowResult).toList());
        return value;
    }

    private Map<String, Object> candidateFilterShadowResult(DiagnosticsCandidateFilterShadowResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", result.filterName());
        value.put("scope", result.scope());
        value.put("evaluations", result.evaluations());
        value.put("wouldPass", result.wouldPass());
        value.put("wouldFilter", result.wouldFilter());
        value.put("passRate", result.passRate());
        value.put("filterRate", result.filterRate());
        value.put("realBetsObserved", result.realBetsObserved());
        value.put("paperTradesObserved", result.paperTradesObserved());
        value.put("settledIncluded", result.settledIncluded());
        value.put("settledExcluded", result.settledExcluded());
        value.put("baselinePnl", result.baselinePnl());
        value.put("includedPnl", result.shadowIncludedPnl());
        value.put("excludedPnl", result.shadowExcludedPnl());
        value.put("baselineRoi", result.baselineRoi());
        value.put("includedRoi", result.shadowIncludedRoi());
        value.put("excludedRoi", result.shadowExcludedRoi());
        value.put("deltaPnl", result.deltaPnl());
        value.put("deltaRoi", result.deltaRoi());
        value.put("maxDrawdownIncluded", result.maxDrawdownIncluded());
        value.put("volumeRetentionPct", result.volumeRetentionPct());
        value.put("status", result.status());
        value.put("warning", result.warning());
        value.put("shouldApplyLive", result.shouldApplyLive());
        return value;
    }

    private Map<String, Object> stakeSizingShadowDiagnostics(DiagnosticsStakeSizingShadowDiagnostics diagnostics) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enabled", diagnostics.enabled());
        value.put("officiallyApplied", diagnostics.officiallyApplied());
        value.put("shouldApplyLive", diagnostics.shouldApplyLive());
        value.put("summary", stakeSizingSummary(diagnostics.summary()));
        value.put("policyResults", diagnostics.policyResults().stream().map(this::stakeSizingPolicyResult).toList());
        value.put("recommendedNextAction", diagnostics.recommendedNextAction());
        return value;
    }

    private Map<String, Object> stakeSizingSummary(DiagnosticsStakeSizingSummary summary) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("decisions", summary.decisions());
        value.put("distinctRecommendations", summary.distinctRecommendations());
        value.put("policies", summary.policies());
        value.put("riskProfiles", summary.riskProfiles());
        value.put("sources", summary.sources());
        value.put("totalObservedCount", summary.totalObservedCount());
        value.put("firstCreatedAt", instant(summary.firstCreatedAt()));
        value.put("lastEvaluatedAt", instant(summary.lastEvaluatedAt()));
        value.put("freshnessMs", millis(summary.freshness()));
        value.put("duplicateLogicalKeys", summary.duplicateLogicalKeys());
        value.put("shadowFailures", summary.shadowFailures());
        value.put("forbiddenLiveStakeEvents", summary.forbiddenLiveStakeEvents());
        return value;
    }

    private Map<String, Object> stakeSizingPolicyResult(DiagnosticsStakeSizingPolicyResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("policyName", result.policyName());
        value.put("riskProfile", result.riskProfile());
        value.put("source", result.source());
        value.put("decisions", result.decisions());
        value.put("distinctRecommendations", result.distinctRecommendations());
        value.put("observations", result.observations());
        value.put("avgBaseStake", result.avgBaseStake());
        value.put("avgCalculatedStake", result.avgCalculatedStake());
        value.put("avgFinalStake", result.avgFinalStake());
        value.put("minCalculatedStake", result.minCalculatedStake());
        value.put("maxCalculatedStake", result.maxCalculatedStake());
        value.put("minFinalStake", result.minFinalStake());
        value.put("maxFinalStake", result.maxFinalStake());
        value.put("wouldBlockCount", result.wouldBlockCount());
        value.put("wouldBlockRate", result.wouldBlockRate());
        value.put("decisionReasonBreakdown", result.decisionReasonBreakdown());
        value.put("blockReasonBreakdown", result.blockReasonBreakdown());
        value.put("adjustmentSummaryBreakdown", result.adjustmentBreakdown());
        value.put("minStakeFloor", result.minStakeFloor());
        value.put("realJoined", result.realJoined());
        value.put("paperJoined", result.paperJoined());
        value.put("probabilityAvailableCount", result.probabilityAvailableCount());
        value.put("probabilityMissingCount", result.probabilityMissingCount());
        value.put("confidenceAvailableCount", result.confidenceAvailableCount());
        value.put("confidenceMissingCount", result.confidenceMissingCount());
        value.put("strongestReductions", result.strongestReductions());
        value.put("status", result.status());
        value.put("warning", result.warning());
        value.put("shouldApplyLive", result.shouldApplyLive());
        return value;
    }

    private Map<String, Object> stakeSizingScenarioSimulation(DiagnosticsStakeSizingScenarioSimulation simulation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enabled", simulation.enabled());
        value.put("officiallyApplied", simulation.officiallyApplied());
        value.put("shouldApplyLive", simulation.shouldApplyLive());
        value.put("scenarios", simulation.scenarios().stream().map(this::stakeSizingScenario).toList());
        value.put("ranking", simulation.ranking());
        return value;
    }

    private Map<String, Object> stakeSizingScenario(DiagnosticsStakeSizingScenario scenario) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("scenarioName", scenario.scenarioName());
        value.put("baseStake", scenario.baseStake());
        value.put("minStake", scenario.minStake());
        value.put("maxStake", scenario.maxStake());
        value.put("policyResults", scenario.policyResults());
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
