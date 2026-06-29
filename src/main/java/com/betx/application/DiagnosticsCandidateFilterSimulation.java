package com.betx.application;

import java.util.List;

/** Diagnostics-only comparison of candidate filters against the current historical baseline. */
public record DiagnosticsCandidateFilterSimulation(
    DiagnosticsStrategyPerformanceSegment baseline,
    List<DiagnosticsCandidateFilterResult> results,
    List<DiagnosticsCandidateFilterResult> bestDeltaPnl,
    List<DiagnosticsCandidateFilterResult> bestRoi,
    List<DiagnosticsCandidateFilterResult> bestDrawdownReduction,
    List<DiagnosticsCandidateFilterResult> bestRiskAdjustedCandidates,
    List<DiagnosticsCandidateFilterResult> worstVolumeLoss,
    DiagnosticsStrategyExperimentRecommendation recommendation
) {
    public DiagnosticsCandidateFilterSimulation {
        baseline = baseline == null ? DiagnosticsStrategyPerformanceSegment.empty("baseline") : baseline;
        results = results == null ? List.of() : List.copyOf(results);
        bestDeltaPnl = bestDeltaPnl == null ? List.of() : List.copyOf(bestDeltaPnl);
        bestRoi = bestRoi == null ? List.of() : List.copyOf(bestRoi);
        bestDrawdownReduction = bestDrawdownReduction == null ? List.of() : List.copyOf(bestDrawdownReduction);
        bestRiskAdjustedCandidates = bestRiskAdjustedCandidates == null ? List.of() : List.copyOf(bestRiskAdjustedCandidates);
        worstVolumeLoss = worstVolumeLoss == null ? List.of() : List.copyOf(worstVolumeLoss);
        recommendation = recommendation == null ? DiagnosticsStrategyExperimentRecommendation.none() : recommendation;
    }

    public static DiagnosticsCandidateFilterSimulation empty() {
        return new DiagnosticsCandidateFilterSimulation(
            DiagnosticsStrategyPerformanceSegment.empty("baseline"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            DiagnosticsStrategyExperimentRecommendation.none()
        );
    }
}
