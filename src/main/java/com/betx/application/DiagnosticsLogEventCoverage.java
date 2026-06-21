package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;

/** Operational event counts observed in structured JSONL logs. */
public record DiagnosticsLogEventCoverage(
    long orderSubmittedEvents,
    long orderResponseEvents,
    long orderAcceptedEvents,
    long orderRejectedEvents,
    long orderUnmatchedEvents,
    long orderPartiallyMatchedEvents,
    long orderMatchedEvents,
    long orderSettledEvents,
    long activeMarketSkips,
    long atomicDuplicateBlocks,
    DiagnosticsDataProvenance provenance
) {
}
