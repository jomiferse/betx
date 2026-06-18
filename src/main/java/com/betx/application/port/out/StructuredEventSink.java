package com.betx.application.port.out;

import com.betx.application.observability.BetxEvent;
import com.betx.domain.config.StructuredLogsConfig;

@FunctionalInterface
public interface StructuredEventSink {
    void emit(BetxEvent event);

    default void configure(StructuredLogsConfig config, boolean enabled) {
    }

    static StructuredEventSink noop() {
        return ignored -> {
        };
    }
}
