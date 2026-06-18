package com.betx.application.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.StructuredEventSink;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetxEventLoggerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-18T18:20:31.123Z"), ZoneOffset.UTC);

    @Test
    void emitsVersionedUtcStructuredEvent() {
        RecordingSink sink = new RecordingSink();
        BetxEventLogger logger = new BetxEventLogger(sink, CLOCK);

        logger.info(BetxEventCategory.ANALYTICS, "signal.generated")
            .correlationId("sig-betfair-1.234-42")
            .cycleId("cycle-20260618T182031Z-01")
            .exchange("betfair")
            .marketId("1.234")
            .selectionId(42L)
            .strategy("value-football")
            .executionMode("automatic")
            .result("accepted")
            .field("odds", "3.40")
            .emit();

        assertThat(sink.events()).singleElement().satisfies(event -> {
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-06-18T18:20:31.123Z"));
            assertThat(event.level()).isEqualTo(BetxEventLevel.INFO);
            assertThat(event.category()).isEqualTo(BetxEventCategory.ANALYTICS);
            assertThat(event.event()).isEqualTo("signal.generated");
            assertThat(event.correlationId()).isEqualTo("sig-betfair-1.234-42");
            assertThat(event.cycleId()).isEqualTo("cycle-20260618T182031Z-01");
            assertThat(event.exchange()).isEqualTo("betfair");
            assertThat(event.marketId()).isEqualTo("1.234");
            assertThat(event.selectionId()).isEqualTo(42L);
            assertThat(event.strategy()).isEqualTo("value-football");
            assertThat(event.executionMode()).isEqualTo("automatic");
            assertThat(event.result()).isEqualTo("accepted");
            assertThat(event.fields()).containsEntry("odds", "3.40");
        });
    }

    @Test
    void keepsLoggingFailuresOutOfApplicationFlow() {
        BetxEventLogger logger = new BetxEventLogger(event -> {
            throw new IllegalStateException("disk full");
        }, CLOCK);

        logger.warn(BetxEventCategory.ERROR, "dependency.error")
            .result("failed")
            .fields(Map.of("dependency", "sqlite"))
            .emit();
    }

    private static final class RecordingSink implements StructuredEventSink {
        private final List<BetxEvent> events = new ArrayList<>();

        @Override
        public void emit(BetxEvent event) {
            events.add(event);
        }

        private List<BetxEvent> events() {
            return events;
        }
    }
}
