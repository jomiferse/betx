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
    DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort,
    List<DiagnosticsSkippedMarket> topSkippedMarkets,
    DiagnosticsBetRecommendationsSummary betRecommendations,
    DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage,
    DiagnosticsRecommendationReadiness recommendationReadiness,
    DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview,
    DiagnosticsRecommendationDivergenceAnalysis recommendationDivergenceAnalysis,
    DiagnosticsStrategyPerformance strategyPerformance,
    DiagnosticsCandidateFilterSimulation candidateFilterSimulation,
    DiagnosticsCandidateFilterShadowValidation candidateFilterShadowValidation
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
            new DiagnosticsLogEventCoverage(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, DiagnosticsModel.DiagnosticsDataProvenance.UNAVAILABLE),
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
            ),
            List.of(),
            DiagnosticsBetRecommendationsSummary.empty(),
            DiagnosticsPaperRecommendationCoverage.empty(),
            DiagnosticsRecommendationReadiness.empty(),
            DiagnosticsRecommendationIdMatchingPreview.empty(),
            DiagnosticsRecommendationDivergenceAnalysis.empty(),
            DiagnosticsStrategyPerformance.empty(),
            DiagnosticsCandidateFilterSimulation.empty(),
            DiagnosticsCandidateFilterShadowValidation.empty()
        );
    }

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
        List<DiagnosticsMatch> matchedPairs,
        Map<MatchGapReason, Long> matchingGaps,
        DiagnosticsExecutionDataCoverage executionDataCoverage,
        DiagnosticsLogEventCoverage logEventCoverage,
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
        DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration,
        DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort,
        List<DiagnosticsSkippedMarket> topSkippedMarkets,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage,
        DiagnosticsRecommendationReadiness recommendationReadiness
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
            matchingGaps,
            executionDataCoverage,
            logEventCoverage,
            persistedExecutionCoverage,
            placeOrdersResponseDuration,
            prospectiveRealBettingCohort,
            topSkippedMarkets,
            betRecommendations,
            paperRecommendationCoverage,
            recommendationReadiness,
            DiagnosticsRecommendationIdMatchingPreview.empty(),
            DiagnosticsRecommendationDivergenceAnalysis.empty(),
            DiagnosticsStrategyPerformance.empty(),
            DiagnosticsCandidateFilterSimulation.empty()
        );
    }

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
        List<DiagnosticsMatch> matchedPairs,
        Map<MatchGapReason, Long> matchingGaps,
        DiagnosticsExecutionDataCoverage executionDataCoverage,
        DiagnosticsLogEventCoverage logEventCoverage,
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
        DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration,
        DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort,
        List<DiagnosticsSkippedMarket> topSkippedMarkets,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage,
        DiagnosticsRecommendationReadiness recommendationReadiness,
        DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview
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
            matchingGaps,
            executionDataCoverage,
            logEventCoverage,
            persistedExecutionCoverage,
            placeOrdersResponseDuration,
            prospectiveRealBettingCohort,
            topSkippedMarkets,
            betRecommendations,
            paperRecommendationCoverage,
            recommendationReadiness,
            recommendationIdMatchingPreview,
            DiagnosticsRecommendationDivergenceAnalysis.empty(),
            DiagnosticsStrategyPerformance.empty(),
            DiagnosticsCandidateFilterSimulation.empty(),
            DiagnosticsCandidateFilterShadowValidation.empty()
        );
    }

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
        List<DiagnosticsMatch> matchedPairs,
        Map<MatchGapReason, Long> matchingGaps,
        DiagnosticsExecutionDataCoverage executionDataCoverage,
        DiagnosticsLogEventCoverage logEventCoverage,
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
        DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration,
        DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort,
        List<DiagnosticsSkippedMarket> topSkippedMarkets,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage,
        DiagnosticsRecommendationReadiness recommendationReadiness,
        DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview,
        DiagnosticsRecommendationDivergenceAnalysis recommendationDivergenceAnalysis
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
            matchingGaps,
            executionDataCoverage,
            logEventCoverage,
            persistedExecutionCoverage,
            placeOrdersResponseDuration,
            prospectiveRealBettingCohort,
            topSkippedMarkets,
            betRecommendations,
            paperRecommendationCoverage,
            recommendationReadiness,
            recommendationIdMatchingPreview,
            recommendationDivergenceAnalysis,
            DiagnosticsStrategyPerformance.empty(),
            DiagnosticsCandidateFilterSimulation.empty(),
            DiagnosticsCandidateFilterShadowValidation.empty()
        );
    }

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
        List<DiagnosticsMatch> matchedPairs,
        Map<MatchGapReason, Long> matchingGaps,
        DiagnosticsExecutionDataCoverage executionDataCoverage,
        DiagnosticsLogEventCoverage logEventCoverage,
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
        DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration,
        DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort,
        List<DiagnosticsSkippedMarket> topSkippedMarkets,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage,
        DiagnosticsRecommendationReadiness recommendationReadiness,
        DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview,
        DiagnosticsRecommendationDivergenceAnalysis recommendationDivergenceAnalysis,
        DiagnosticsStrategyPerformance strategyPerformance,
        DiagnosticsCandidateFilterSimulation candidateFilterSimulation
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
            matchingGaps,
            executionDataCoverage,
            logEventCoverage,
            persistedExecutionCoverage,
            placeOrdersResponseDuration,
            prospectiveRealBettingCohort,
            topSkippedMarkets,
            betRecommendations,
            paperRecommendationCoverage,
            recommendationReadiness,
            recommendationIdMatchingPreview,
            recommendationDivergenceAnalysis,
            strategyPerformance,
            candidateFilterSimulation,
            DiagnosticsCandidateFilterShadowValidation.empty()
        );
    }

    public DiagnosticsReport {
        paperRecommendationCoverage = paperRecommendationCoverage == null
            ? DiagnosticsPaperRecommendationCoverage.empty()
            : paperRecommendationCoverage;
        recommendationReadiness = recommendationReadiness == null
            ? DiagnosticsRecommendationReadiness.empty()
            : recommendationReadiness;
        recommendationIdMatchingPreview = recommendationIdMatchingPreview == null
            ? DiagnosticsRecommendationIdMatchingPreview.empty()
            : recommendationIdMatchingPreview;
        recommendationDivergenceAnalysis = recommendationDivergenceAnalysis == null
            ? DiagnosticsRecommendationDivergenceAnalysis.empty()
            : recommendationDivergenceAnalysis;
        strategyPerformance = strategyPerformance == null ? DiagnosticsStrategyPerformance.empty() : strategyPerformance;
        candidateFilterSimulation = candidateFilterSimulation == null
            ? DiagnosticsCandidateFilterSimulation.empty()
            : candidateFilterSimulation;
        candidateFilterShadowValidation = candidateFilterShadowValidation == null
            ? DiagnosticsCandidateFilterShadowValidation.empty()
            : candidateFilterShadowValidation;
    }
}
