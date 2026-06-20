package com.betx.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.betx.application.DiagnosticsModel.MatchGapReason;

public record DiagnosticsReport(
    Instant generatedAt,
    DiagnosticsPeriod period,
    DiagnosticsCoverage coverage,
    DiagnosticsDecisionFunnel decisionFunnel,
    DiagnosticsExecutionMetrics executionMetrics,
    DiagnosticsPaperVsRealMetrics paperVsRealMetrics,
    List<DiagnosticFinding> integrityFindings,
    List<String> limitations,
    List<String> topFindings,
    List<DiagnosticsMatch> matchedPairs,
    Map<MatchGapReason, Long> matchingGaps,
    DiagnosticsExecutionDataCoverage executionDataCoverage,
    DiagnosticsLogEventCoverage logEventCoverage,
    DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
    DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration,
    DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort
) {
    public DiagnosticsReport(
        Instant generatedAt,
        DiagnosticsPeriod period,
        DiagnosticsCoverage coverage,
        DiagnosticsDecisionFunnel decisionFunnel,
        DiagnosticsExecutionMetrics executionMetrics,
        DiagnosticsPaperVsRealMetrics paperVsRealMetrics,
        List<DiagnosticFinding> integrityFindings,
        List<String> limitations,
        List<String> topFindings,
        List<DiagnosticsMatch> matchedPairs
    ) {
        this(
            generatedAt,
            period,
            coverage,
            decisionFunnel,
            executionMetrics,
            paperVsRealMetrics,
            integrityFindings,
            limitations,
            topFindings,
            matchedPairs,
            Map.of(),
            new DiagnosticsExecutionDataCoverage(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new DiagnosticsLogEventCoverage(0, 0, 0, 0, DiagnosticsModel.DiagnosticsDataProvenance.UNAVAILABLE),
            new DiagnosticsPersistedExecutionCoverage(0, 0, 0, 0, 0, 0, 0, 0, DiagnosticsModel.DiagnosticsDataProvenance.UNAVAILABLE),
            new DiagnosticsPlaceOrdersResponseDuration(
                0,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                DiagnosticsModel.DiagnosticsDataProvenance.UNAVAILABLE
            ),
            new DiagnosticsProspectiveRealBettingCohort(
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                DiagnosticsModel.DiagnosticsDataProvenance.UNAVAILABLE
            )
        );
    }
}
