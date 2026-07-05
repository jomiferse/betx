package com.betx.application;

import java.util.List;

/** Read-only diagnostics for persisted stake sizing shadow decisions. */
public record DiagnosticsStakeSizingShadowDiagnostics(
    boolean enabled,
    boolean officiallyApplied,
    boolean shouldApplyLive,
    DiagnosticsStakeSizingSummary summary,
    List<DiagnosticsStakeSizingPolicyResult> policyResults,
    String recommendedNextAction
) {
    public DiagnosticsStakeSizingShadowDiagnostics {
        officiallyApplied = false;
        shouldApplyLive = false;
        summary = summary == null ? DiagnosticsStakeSizingSummary.empty() : summary;
        policyResults = policyResults == null ? List.of() : List.copyOf(policyResults);
        recommendedNextAction = recommendedNextAction == null || recommendedNextAction.isBlank()
            ? "keep shadow running; do not enable live staking; collect more settled joined bets"
            : recommendedNextAction.strip();
    }

    public static DiagnosticsStakeSizingShadowDiagnostics empty() {
        return new DiagnosticsStakeSizingShadowDiagnostics(false, false, false, DiagnosticsStakeSizingSummary.empty(), List.of(), null);
    }
}
