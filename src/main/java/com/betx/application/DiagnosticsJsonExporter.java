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
        payload.put("decisionFunnel", report.decisionFunnel());
        payload.put("executionMetrics", execution(report.executionMetrics()));
        payload.put("logEventCoverage", report.logEventCoverage());
        payload.put("persistedExecutionCoverage", report.persistedExecutionCoverage());
        payload.put("placeOrdersResponseDuration", placeOrdersResponseDuration(report.placeOrdersResponseDuration()));
        payload.put("prospectiveRealBettingCohort", report.prospectiveRealBettingCohort());
        payload.put("executionDataCoverage", report.executionDataCoverage());
        payload.put("matchingGaps", report.matchingGaps());
        payload.put("paperVsRealMetrics", report.paperVsRealMetrics());
        payload.put("integrityFindings", report.integrityFindings());
        payload.put("limitations", report.limitations());
        payload.put("topFindings", report.topFindings());
        payload.put("matchedPairs", report.matchedPairs().stream().map(this::match).toList());
        return payload;
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
