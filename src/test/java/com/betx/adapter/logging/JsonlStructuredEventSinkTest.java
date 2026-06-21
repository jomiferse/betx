package com.betx.adapter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLevel;
import com.betx.domain.config.StructuredLogsConfig;
import com.betx.domain.order.BetExecutionStatus;
import com.betx.domain.order.BetIntentStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlStructuredEventSinkTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-18T18:20:31Z"), ZoneOffset.UTC);

    @Test
    void writesOneJsonLineToCategoryFile(@TempDir Path tempDir) throws Exception {
        JsonlStructuredEventSink sink = new JsonlStructuredEventSink(tempDir.resolve("events"), 30, CLOCK);

        sink.emit(new BetxEvent(
            1,
            Instant.parse("2026-06-18T18:20:31Z"),
            BetxEventLevel.INFO,
            BetxEventCategory.ANALYTICS,
            "signal.generated",
            "correlation-1",
            "cycle-1",
            "betfair",
            "1.234",
            42L,
            "value-football",
            "automatic",
            "accepted",
            Map.of("api_key", "secret", "odds", "3.40")
        ));

        Path file = tempDir.resolve("events").resolve("analytics_2026-06-18.jsonl");
        assertThat(file).exists();
        JsonNode node = new ObjectMapper().readTree(Files.readString(file).strip());
        assertThat(node.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(node.path("timestamp").asText()).isEqualTo("2026-06-18T18:20:31Z");
        assertThat(node.path("event").asText()).isEqualTo("signal.generated");
        assertThat(node.path("fields").path("api_key").asText()).isEqualTo("[REDACTED]");
        assertThat(node.path("fields").path("odds").asText()).isEqualTo("3.40");
    }

    @Test
    void deletesOnlyExpiredJsonlFiles(@TempDir Path tempDir) throws Exception {
        Path events = tempDir.resolve("events");
        Files.createDirectories(events);
        Path oldJsonl = events.resolve("audit_2026-05-01.jsonl");
        Path currentJsonl = events.resolve("audit_2026-06-18.jsonl");
        Path textLog = events.resolve("messages_01052026.txt");
        Files.writeString(oldJsonl, "{}\n");
        Files.writeString(currentJsonl, "{}\n");
        Files.writeString(textLog, "keep\n");

        new JsonlStructuredEventSink(events, 30, CLOCK).applyRetention();

        assertThat(oldJsonl).doesNotExist();
        assertThat(currentJsonl).exists();
        assertThat(textLog).exists();
    }

    @Test
    void appliesRuntimeConfiguration(@TempDir Path tempDir) {
        JsonlStructuredEventSink sink = new JsonlStructuredEventSink(tempDir.resolve("events"), 30, CLOCK);
        sink.configure(new StructuredLogsConfig(false, tempDir.resolve("custom").toString(), 30), true);

        sink.emit(event());

        assertThat(tempDir.resolve("custom")).doesNotExist();

        sink.configure(new StructuredLogsConfig(true, tempDir.resolve("custom").toString(), 30), true);
        sink.emit(event());

        assertThat(tempDir.resolve("custom").resolve("operational_2026-06-18.jsonl")).exists();
    }

    @Test
    void writesJsonSafeOrderResponseFields(@TempDir Path tempDir) throws Exception {
        JsonlStructuredEventSink sink = new JsonlStructuredEventSink(tempDir.resolve("events"), 30, CLOCK);

        sink.emit(new BetxEvent(
            1,
            Instant.parse("2026-06-18T18:20:31Z"),
            BetxEventLevel.INFO,
            BetxEventCategory.AUDIT,
            "order.response",
            "intent-1",
            null,
            "betfair",
            "1.234",
            42L,
            "value-football",
            "automatic",
            "response",
            Map.of(
                "orderSubmittedAt", Instant.parse("2026-06-18T18:20:30Z"),
                "orderResponseAt", Instant.parse("2026-06-18T18:20:31Z"),
                "stage", BetIntentStage.EXECUTED,
                "executionStatus", BetExecutionStatus.UNMATCHED,
                "requestedOdds", BigDecimal.valueOf(2.5)
            )
        ));

        Path file = tempDir.resolve("events").resolve("audit_2026-06-18.jsonl");
        JsonNode node = new ObjectMapper().readTree(Files.readString(file).strip());
        assertThat(node.path("event").asText()).isEqualTo("order.response");
        assertThat(node.path("fields").path("orderSubmittedAt").asText()).isEqualTo("2026-06-18T18:20:30Z");
        assertThat(node.path("fields").path("orderResponseAt").asText()).isEqualTo("2026-06-18T18:20:31Z");
        assertThat(node.path("fields").path("stage").asText()).isEqualTo("EXECUTED");
        assertThat(node.path("fields").path("executionStatus").asText()).isEqualTo("UNMATCHED");
        assertThat(node.path("fields").path("requestedOdds").decimalValue()).isEqualByComparingTo("2.5");
    }

    private BetxEvent event() {
        return new BetxEvent(
            1,
            Instant.parse("2026-06-18T18:20:31Z"),
            BetxEventLevel.INFO,
            BetxEventCategory.OPERATIONAL,
            "cycle.started",
            "correlation-1",
            "cycle-1",
            null,
            null,
            null,
            null,
            "paper",
            "started",
            Map.of()
        );
    }
}
