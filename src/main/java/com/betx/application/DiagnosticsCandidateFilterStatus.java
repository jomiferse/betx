package com.betx.application;

/** Non-executable diagnostics verdict for a simulated candidate filter. */
public enum DiagnosticsCandidateFilterStatus {
    CANDIDATE,
    WEAK_EVIDENCE,
    INSUFFICIENT_SAMPLE,
    REJECTED,
    OVERFIT_RISK
}
