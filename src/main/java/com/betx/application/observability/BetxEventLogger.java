package com.betx.application.observability;

import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.AppConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BetxEventLogger {
    private final StructuredEventSink sink;
    private final Clock clock;

    @Autowired
    public BetxEventLogger(StructuredEventSink sink) {
        this(sink, Clock.systemUTC());
    }

    public BetxEventLogger(StructuredEventSink sink, Clock clock) {
        this.sink = sink == null ? StructuredEventSink.noop() : sink;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public EventBuilder trace(BetxEventCategory category, String event) {
        return builder(BetxEventLevel.TRACE, category, event);
    }

    public EventBuilder debug(BetxEventCategory category, String event) {
        return builder(BetxEventLevel.DEBUG, category, event);
    }

    public EventBuilder info(BetxEventCategory category, String event) {
        return builder(BetxEventLevel.INFO, category, event);
    }

    public EventBuilder warn(BetxEventCategory category, String event) {
        return builder(BetxEventLevel.WARN, category, event);
    }

    public EventBuilder error(BetxEventCategory category, String event) {
        return builder(BetxEventLevel.ERROR, category, event);
    }

    private EventBuilder builder(BetxEventLevel level, BetxEventCategory category, String event) {
        return new EventBuilder(BetxEvent.builder(Instant.now(clock), level, category, event));
    }

    public void configure(AppConfig appConfig) {
        if (appConfig == null) {
            return;
        }
        boolean enabled = "info".equalsIgnoreCase(appConfig.logLevel());
        sink.configure(appConfig.structuredLogs(), enabled);
    }

    public final class EventBuilder {
        private final BetxEvent.Builder delegate;

        private EventBuilder(BetxEvent.Builder delegate) {
            this.delegate = delegate;
        }

        public EventBuilder correlationId(String value) {
            delegate.correlationId(value);
            return this;
        }

        public EventBuilder cycleId(String value) {
            delegate.cycleId(value);
            return this;
        }

        public EventBuilder exchange(String value) {
            delegate.exchange(value);
            return this;
        }

        public EventBuilder marketId(String value) {
            delegate.marketId(value);
            return this;
        }

        public EventBuilder selectionId(Long value) {
            delegate.selectionId(value);
            return this;
        }

        public EventBuilder strategy(String value) {
            delegate.strategy(value);
            return this;
        }

        public EventBuilder executionMode(String value) {
            delegate.executionMode(value);
            return this;
        }

        public EventBuilder result(String value) {
            delegate.result(value);
            return this;
        }

        public EventBuilder field(String key, Object value) {
            delegate.field(key, value);
            return this;
        }

        public EventBuilder fields(Map<String, ?> values) {
            delegate.fields(values);
            return this;
        }

        public void emit() {
            try {
                sink.emit(delegate.build());
            } catch (RuntimeException ignored) {
                // Structured logging must never break betting, Telegram, or paper-trading flows.
            }
        }
    }
}
