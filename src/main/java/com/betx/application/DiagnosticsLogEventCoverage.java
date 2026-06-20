package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;

/** Operational event counts observed in structured JSONL logs. */
public record DiagnosticsLogEventCoverage(
    long orderSubmittedEvents,
    long orderAcceptedEvents,
    long orderRejectedEvents,
    long orderSettledEvents,
    DiagnosticsDataProvenance provenance
) {
}
