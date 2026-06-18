package com.betx.application.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record BetxEvent(
    int schemaVersion,
    Instant timestamp,
    BetxEventLevel level,
    BetxEventCategory category,
    String event,
    String correlationId,
    String cycleId,
    String exchange,
    String marketId,
    Long selectionId,
    String strategy,
    String executionMode,
    String result,
    Map<String, Object> fields
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public BetxEvent {
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        timestamp = timestamp == null ? Instant.now() : timestamp;
        level = level == null ? BetxEventLevel.INFO : level;
        category = category == null ? BetxEventCategory.OPERATIONAL : category;
        event = event == null || event.isBlank() ? "unknown" : event;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static Builder builder(Instant timestamp, BetxEventLevel level, BetxEventCategory category, String event) {
        return new Builder(timestamp, level, category, event);
    }

    public static final class Builder {
        private final Instant timestamp;
        private final BetxEventLevel level;
        private final BetxEventCategory category;
        private final String event;
        private String correlationId;
        private String cycleId;
        private String exchange;
        private String marketId;
        private Long selectionId;
        private String strategy;
        private String executionMode;
        private String result;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(Instant timestamp, BetxEventLevel level, BetxEventCategory category, String event) {
            this.timestamp = timestamp;
            this.level = level;
            this.category = category;
            this.event = event;
        }

        public Builder correlationId(String value) {
            correlationId = value;
            return this;
        }

        public Builder cycleId(String value) {
            cycleId = value;
            return this;
        }

        public Builder exchange(String value) {
            exchange = value;
            return this;
        }

        public Builder marketId(String value) {
            marketId = value;
            return this;
        }

        public Builder selectionId(Long value) {
            selectionId = value;
            return this;
        }

        public Builder strategy(String value) {
            strategy = value;
            return this;
        }

        public Builder executionMode(String value) {
            executionMode = value;
            return this;
        }

        public Builder result(String value) {
            result = value;
            return this;
        }

        public Builder field(String key, Object value) {
            if (key != null && !key.isBlank()) {
                fields.put(key, value);
            }
            return this;
        }

        public Builder fields(Map<String, ?> values) {
            if (values != null) {
                values.forEach(this::field);
            }
            return this;
        }

        public BetxEvent build() {
            return new BetxEvent(
                CURRENT_SCHEMA_VERSION,
                timestamp,
                level,
                category,
                event,
                correlationId,
                cycleId,
                exchange,
                marketId,
                selectionId,
                strategy,
                executionMode,
                result,
                fields
            );
        }
    }
}
