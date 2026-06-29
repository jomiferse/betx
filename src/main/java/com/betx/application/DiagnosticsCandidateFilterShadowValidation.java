package com.betx.application;

import java.time.Instant;
import java.util.List;

public record DiagnosticsCandidateFilterShadowValidation(
    boolean enabled,
    boolean officiallyApplied,
    Instant post32Cutoff,
    List<DiagnosticsCandidateFilterShadowResult> filters,
    boolean shouldApplyLive
) {
    public DiagnosticsCandidateFilterShadowValidation {
        filters = filters == null ? List.of() : List.copyOf(filters);
    }

    public static DiagnosticsCandidateFilterShadowValidation empty() {
        return new DiagnosticsCandidateFilterShadowValidation(true, false, null, List.of(), false);
    }
}
