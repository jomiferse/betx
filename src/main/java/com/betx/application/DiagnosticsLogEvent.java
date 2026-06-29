package com.betx.application;

import java.time.Instant;

/** Structured log event retained for diagnostics-only correlation. */
public record DiagnosticsLogEvent(
    Instant timestamp,
    String eventName,
    String recommendationId,
    String canonicalKey,
    String exchange,
    String marketId,
    long selectionId,
    String side,
    String strategyName,
    String reason,
    String message,
    String eventDisplayName,
    String runnerName
) {
    public DiagnosticsLogEvent {
        eventName = blankToNull(eventName);
        recommendationId = blankToNull(recommendationId);
        canonicalKey = blankToNull(canonicalKey);
        exchange = blankToNull(exchange);
        marketId = blankToNull(marketId);
        side = blankToNull(side);
        strategyName = blankToNull(strategyName);
        reason = blankToNull(reason);
        message = blankToNull(message);
        eventDisplayName = blankToNull(eventDisplayName);
        runnerName = blankToNull(runnerName);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
