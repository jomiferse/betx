package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.time.Instant;

/** Evidence supporting a diagnostics-only recommendation divergence reason. */
public record DiagnosticsRecommendationDivergenceEvidence(
    String eventName,
    Instant timestamp,
    String message,
    DiagnosticsDataProvenance source,
    String recommendationId
) {
    public DiagnosticsRecommendationDivergenceEvidence {
        source = source == null ? DiagnosticsDataProvenance.UNAVAILABLE : source;
    }
}
