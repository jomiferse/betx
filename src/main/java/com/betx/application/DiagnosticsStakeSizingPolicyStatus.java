package com.betx.application;

/** Diagnostics-only verdict for a shadow stake sizing policy. */
public enum DiagnosticsStakeSizingPolicyStatus {
    INSUFFICIENT_SAMPLE,
    WEAK_EVIDENCE,
    CANDIDATE,
    REJECTED,
    HIGH_RISK,
    SHADOW_ONLY
}
