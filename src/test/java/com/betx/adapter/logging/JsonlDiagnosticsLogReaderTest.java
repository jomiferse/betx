package com.betx.adapter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DiagnosticsLogSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlDiagnosticsLogReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsJsonlIncrementallyAndIgnoresInvalidLines() throws Exception {
        Files.writeString(tempDir.resolve("audit_2026-06-01.jsonl"), """
            {"timestamp":"2026-06-01T10:00:00Z","event":"order.submitted","fields":{"externalOrderId":"abc"}}
            invalid
            {"timestamp":"2026-06-01T10:00:01Z","event":"order.accepted","fields":{"externalOrderId":"abc"}}
            {"timestamp":"2026-05-01T10:00:01Z","event":"order.accepted","fields":{"externalOrderId":"old"}}
            """);

        DiagnosticsLogSummary summary = new JsonlDiagnosticsLogReader().read(
            tempDir,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-06-02T00:00:00Z")
        );

        assertThat(summary.eventCounts()).containsEntry("order.submitted", 1L).containsEntry("order.accepted", 1L);
        assertThat(summary.acceptedLatenciesByExternalOrderId().get("abc").toMillis()).isEqualTo(1000);
        assertThat(summary.invalidLines()).isEqualTo(1);
        assertThat(summary.ignoredLines()).isEqualTo(1);
    }

    @Test
    void handlesMissingLogDirectory() {
        DiagnosticsLogSummary summary = new JsonlDiagnosticsLogReader().read(tempDir.resolve("missing"), null, null);

        assertThat(summary.eventCounts()).isEmpty();
        assertThat(summary.limitations()).isNotEmpty();
    }
}
