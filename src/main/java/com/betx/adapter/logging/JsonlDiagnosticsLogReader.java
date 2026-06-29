package com.betx.adapter.logging;

import com.betx.application.DiagnosticsLogReader;
import com.betx.application.DiagnosticsLogEvent;
import com.betx.application.DiagnosticsLogSummary;
import com.betx.application.DiagnosticsSkippedMarket;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonlDiagnosticsLogReader implements DiagnosticsLogReader {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public DiagnosticsLogSummary read(Path logsDir, Instant from, Instant to) {
        Path directory = logsDir == null ? Path.of("logs", "events") : logsDir;
        if (!Files.isDirectory(directory)) {
            return new DiagnosticsLogSummary(
                Map.of(),
                Map.of(),
                0,
                0,
                List.of("Structured logs directory not found: " + directory + ".")
            );
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Instant> submittedByExternalOrderId = new HashMap<>();
        Map<String, Duration> acceptedLatenciesByExternalOrderId = new HashMap<>();
        Map<String, SkippedMarketAccumulator> skippedMarkets = new HashMap<>();
        List<DiagnosticsLogEvent> diagnosticEvents = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        long invalidLines = 0;
        long ignoredLines = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jsonl")) {
            for (Path file : stream) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ParsedEvent event = parse(line);
                        if (event == null) {
                            invalidLines++;
                            continue;
                        }
                        if (!inPeriod(event.timestamp(), from, to)) {
                            ignoredLines++;
                            continue;
                        }
                        counts.merge(event.event(), 1L, Long::sum);
                        if (event.reason() != null) {
                            counts.merge(event.event() + ":" + event.reason(), 1L, Long::sum);
                        }
                        if ("bet_signal.skipped".equals(event.event())
                            && "ACTIVE_MARKET_INTENT_EXISTS".equals(event.reason())) {
                            skippedMarkets.computeIfAbsent(event.skipKey(), ignored -> new SkippedMarketAccumulator(event))
                                .increment();
                        }
                        if (isDiagnosticEvent(event.event())) {
                            diagnosticEvents.add(event.toDiagnosticEvent());
                        }
                        String externalOrderId = event.externalOrderId();
                        if (externalOrderId == null) {
                            continue;
                        }
                        if ("order.submitted".equals(event.event())) {
                            submittedByExternalOrderId.put(externalOrderId, event.timestamp());
                        } else if ("order.accepted".equals(event.event())) {
                            Instant submittedAt = submittedByExternalOrderId.get(externalOrderId);
                            if (submittedAt != null) {
                                acceptedLatenciesByExternalOrderId.put(externalOrderId, Duration.between(submittedAt, event.timestamp()));
                            }
                        }
                    }
                }
            }
        } catch (IOException exc) {
            limitations.add("Could not read all structured logs: " + exc.getMessage() + ".");
        }
        return new DiagnosticsLogSummary(
            counts,
            acceptedLatenciesByExternalOrderId,
            topSkippedMarkets(skippedMarkets),
            invalidLines,
            ignoredLines,
            limitations,
            diagnosticEvents
        );
    }

    private ParsedEvent parse(String line) {
        try {
            Map<String, Object> json = mapper.readValue(line, new TypeReference<>() {
            });
            Object timestamp = json.get("timestamp");
            Object event = json.get("event");
            if (timestamp == null || event == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = json.get("fields") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : Map.of();
            return new ParsedEvent(
                Instant.parse(String.valueOf(timestamp)),
                String.valueOf(event),
                externalOrderId(json, fields),
                string(fields.get("reason")),
                string(fields.get("message")),
                string(fields.get("resultMessage")),
                string(json.get("exchange")),
                string(json.get("marketId")),
                longValue(json.get("selectionId")),
                coalesce(string(fields.get("side")), string(json.get("side"))),
                coalesce(string(fields.get("strategyName")), string(json.get("strategy"))),
                string(fields.get("recommendationId")),
                string(fields.get("canonicalKey")),
                string(fields.get("existingBetIntentId")),
                string(fields.get("eventName")),
                string(fields.get("runnerName")),
                string(fields.get("existingExecutionStatus"))
            );
        } catch (RuntimeException | IOException exc) {
            return null;
        }
    }

    private static boolean isDiagnosticEvent(String event) {
        return List.of(
            "risk.blocked",
            "bet_intent.skipped",
            "bet_signal.skipped",
            "paper_trade.execution_failed",
            "dependency.error",
            "market.scan.failed",
            "order.rejected",
            "order.response",
            "order.submitted",
            "order.accepted"
        ).contains(event);
    }

    private static List<DiagnosticsSkippedMarket> topSkippedMarkets(Map<String, SkippedMarketAccumulator> skippedMarkets) {
        return skippedMarkets.values().stream()
            .sorted((left, right) -> Long.compare(right.attempts, left.attempts))
            .limit(10)
            .map(SkippedMarketAccumulator::toSkippedMarket)
            .toList();
    }

    private static String string(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private static String externalOrderId(Map<String, Object> json, Map<String, Object> fields) {
        Object direct = json.get("externalOrderId");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct);
        }
        for (String key : List.of("externalOrderId", "external_order_id", "betId", "bet_id", "orderId", "order_id")) {
            Object value = fields.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static String coalesce(String first, String second) {
        return first == null ? second : first;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean inPeriod(Instant timestamp, Instant from, Instant to) {
        if (timestamp == null) {
            return false;
        }
        return (from == null || !timestamp.isBefore(from)) && (to == null || !timestamp.isAfter(to));
    }

    private record ParsedEvent(
        Instant timestamp,
        String event,
        String externalOrderId,
        String reason,
        String message,
        String resultMessage,
        String exchange,
        String marketId,
        long selectionId,
        String side,
        String strategyName,
        String recommendationId,
        String canonicalKey,
        String existingBetIntentId,
        String eventName,
        String runnerName,
        String existingExecutionStatus
    ) {
        private String skipKey() {
            return String.join(
                "|",
                marketId == null ? "" : marketId,
                Long.toString(selectionId),
                side == null ? "" : side,
                existingBetIntentId == null ? "" : existingBetIntentId
            );
        }

        private DiagnosticsLogEvent toDiagnosticEvent() {
            String evidenceMessage = resultMessage == null ? message : resultMessage;
            if (evidenceMessage == null) {
                evidenceMessage = reason;
            }
            return new DiagnosticsLogEvent(
                timestamp,
                event,
                recommendationId,
                canonicalKey,
                exchange,
                marketId,
                selectionId,
                side,
                strategyName,
                reason,
                evidenceMessage,
                eventName,
                runnerName
            );
        }
    }

    private static final class SkippedMarketAccumulator {
        private final ParsedEvent first;
        private long attempts;

        private SkippedMarketAccumulator(ParsedEvent first) {
            this.first = first;
        }

        private void increment() {
            attempts++;
        }

        private DiagnosticsSkippedMarket toSkippedMarket() {
            return new DiagnosticsSkippedMarket(
                first.eventName(),
                first.runnerName(),
                first.marketId(),
                first.selectionId(),
                first.side(),
                first.existingBetIntentId(),
                first.existingExecutionStatus(),
                attempts
            );
        }
    }
}
