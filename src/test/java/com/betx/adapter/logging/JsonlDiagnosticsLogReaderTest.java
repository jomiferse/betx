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
            {"timestamp":"2026-06-01T10:00:00.500Z","event":"order.response","fields":{"externalOrderId":"abc"}}
            invalid
            {"timestamp":"2026-06-01T10:00:01Z","event":"order.accepted","fields":{"externalOrderId":"abc"}}
            {"timestamp":"2026-06-01T10:00:02Z","event":"bet_signal.skipped","exchange":"betfair","marketId":"1.1","selectionId":42,"strategy":"value-football","fields":{"recommendationId":"rec-1","canonicalKey":"betfair|1.1|42|DRAW|value-football","reason":"ACTIVE_MARKET_INTENT_EXISTS","side":"DRAW","existingBetIntentId":"intent-1","eventName":"Team A v Team B","runnerName":"Team A","existingExecutionStatus":"FULLY_MATCHED"}}
            {"timestamp":"2026-06-01T10:00:03Z","event":"bet_intent.skipped","fields":{"reason":"DUPLICATE_REAL_BET"}}
            {"timestamp":"2026-05-01T10:00:01Z","event":"order.accepted","fields":{"externalOrderId":"old"}}
            """);

        DiagnosticsLogSummary summary = new JsonlDiagnosticsLogReader().read(
            tempDir,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-06-02T00:00:00Z")
        );

        assertThat(summary.eventCounts())
            .containsEntry("order.submitted", 1L)
            .containsEntry("order.response", 1L)
            .containsEntry("order.accepted", 1L)
            .containsEntry("bet_signal.skipped:ACTIVE_MARKET_INTENT_EXISTS", 1L)
            .containsEntry("bet_intent.skipped:DUPLICATE_REAL_BET", 1L);
        assertThat(summary.acceptedLatenciesByExternalOrderId().get("abc").toMillis()).isEqualTo(1000);
        assertThat(summary.events())
            .filteredOn(event -> event.eventName().equals("bet_signal.skipped"))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.recommendationId()).isEqualTo("rec-1");
                assertThat(event.canonicalKey()).isEqualTo("betfair|1.1|42|DRAW|value-football");
                assertThat(event.exchange()).isEqualTo("betfair");
                assertThat(event.marketId()).isEqualTo("1.1");
                assertThat(event.selectionId()).isEqualTo(42L);
                assertThat(event.side()).isEqualTo("DRAW");
                assertThat(event.strategyName()).isEqualTo("value-football");
                assertThat(event.reason()).isEqualTo("ACTIVE_MARKET_INTENT_EXISTS");
            });
        assertThat(summary.topSkippedMarkets()).singleElement().satisfies(market -> {
            assertThat(market.eventName()).isEqualTo("Team A v Team B");
            assertThat(market.runnerName()).isEqualTo("Team A");
            assertThat(market.marketId()).isEqualTo("1.1");
            assertThat(market.selectionId()).isEqualTo(42L);
            assertThat(market.existingBetIntentId()).isEqualTo("intent-1");
            assertThat(market.existingExecutionStatus()).isEqualTo("FULLY_MATCHED");
            assertThat(market.attempts()).isEqualTo(1L);
        });
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
