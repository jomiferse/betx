package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.time.Duration;

/** Duration between submitting placeOrders and receiving its response. */
public record DiagnosticsPlaceOrdersResponseDuration(
    long observations,
    Duration average,
    Duration median,
    Duration p95,
    Duration minimum,
    Duration maximum,
    long responseBeforeSubmission,
    long missingOrderResponse,
    long slowResponses,
    DiagnosticsDataProvenance provenance
) {
}
