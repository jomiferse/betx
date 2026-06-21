package com.betx.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record DiagnosticsLogSummary(
    Map<String, Long> eventCounts,
    Map<String, Duration> acceptedLatenciesByExternalOrderId,
    List<DiagnosticsSkippedMarket> topSkippedMarkets,
    long invalidLines,
    long ignoredLines,
    List<String> limitations
) {
    public DiagnosticsLogSummary {
        eventCounts = eventCounts == null ? Map.of() : Map.copyOf(eventCounts);
        acceptedLatenciesByExternalOrderId = acceptedLatenciesByExternalOrderId == null
            ? Map.of()
            : Map.copyOf(acceptedLatenciesByExternalOrderId);
        topSkippedMarkets = topSkippedMarkets == null ? List.of() : List.copyOf(topSkippedMarkets);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public DiagnosticsLogSummary(
        Map<String, Long> eventCounts,
        Map<String, Duration> acceptedLatenciesByExternalOrderId,
        long invalidLines,
        long ignoredLines,
        List<String> limitations
    ) {
        this(eventCounts, acceptedLatenciesByExternalOrderId, List.of(), invalidLines, ignoredLines, limitations);
    }

    public static DiagnosticsLogSummary empty() {
        return new DiagnosticsLogSummary(Map.of(), Map.of(), List.of(), 0, 0, List.of());
    }
}
