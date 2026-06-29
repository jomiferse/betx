package com.betx.application;

/** Non-executable next experiment suggestion derived from diagnostics simulation. */
public record DiagnosticsStrategyExperimentRecommendation(
    String filterName,
    String reason,
    String evidence,
    String risk,
    boolean shouldApplyLive
) {
    public static DiagnosticsStrategyExperimentRecommendation none() {
        return new DiagnosticsStrategyExperimentRecommendation(
            "N/A",
            "No candidate filter has enough diagnostics-only evidence.",
            "No simulated candidate passed the conservative ranking gate.",
            "Keep collecting sample before changing live strategy.",
            false
        );
    }
}
