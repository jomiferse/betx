package com.betx.adapter.logging;

import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.StructuredLogsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonlStructuredEventSink implements StructuredEventSink {
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private Path directory;
    private int retentionDays;
    private boolean enabled = true;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final SensitiveFieldSanitizer sanitizer;

    public JsonlStructuredEventSink() {
        this(Path.of("logs", "events"), DEFAULT_RETENTION_DAYS, Clock.systemUTC());
    }

    public JsonlStructuredEventSink(Path directory, int retentionDays, Clock clock) {
        this.directory = directory == null ? Path.of("logs", "events") : directory;
        this.retentionDays = Math.max(retentionDays, 1);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.sanitizer = new SensitiveFieldSanitizer();
        applyRetention();
    }

    @Override
    public synchronized void emit(BetxEvent event) {
        if (!enabled || event == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(
                logFile(event.category()),
                mapper.writeValueAsString(toJsonObject(event)) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exc) {
            throw new IllegalStateException("Could not write structured BetX event log.", exc);
        }
    }

    @Override
    public synchronized void configure(StructuredLogsConfig config, boolean logLevelEnabled) {
        StructuredLogsConfig effective = config == null ? new StructuredLogsConfig(null, null, null) : config;
        enabled = logLevelEnabled && effective.enabled();
        directory = Path.of(effective.directory());
        retentionDays = Math.max(effective.retentionDays(), 1);
        applyRetention();
    }

    public synchronized void applyRetention() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        LocalDate cutoff = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(retentionDays);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jsonl")) {
            for (Path file : stream) {
                eventFileDate(file)
                    .filter(date -> date.isBefore(cutoff))
                    .ifPresent(ignored -> deleteQuietly(file));
            }
        } catch (IOException exc) {
            throw new IllegalStateException("Could not apply structured log retention.", exc);
        }
    }

    private Map<String, Object> toJsonObject(BetxEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", event.schemaVersion());
        json.put("timestamp", event.timestamp().toString());
        json.put("level", event.level().name());
        json.put("category", event.category().name());
        json.put("event", event.event());
        putIfPresent(json, "correlationId", event.correlationId());
        putIfPresent(json, "cycleId", event.cycleId());
        putIfPresent(json, "exchange", event.exchange());
        putIfPresent(json, "marketId", event.marketId());
        putIfPresent(json, "selectionId", event.selectionId());
        putIfPresent(json, "strategy", event.strategy());
        putIfPresent(json, "executionMode", event.executionMode());
        putIfPresent(json, "result", event.result());
        json.put("fields", sanitizer.sanitize(event.fields()));
        return json;
    }

    private void putIfPresent(Map<String, Object> json, String key, Object value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    private Path logFile(BetxEventCategory category) {
        String prefix = switch (category == null ? BetxEventCategory.OPERATIONAL : category) {
            case OPERATIONAL -> "operational";
            case ANALYTICS -> "analytics";
            case AUDIT -> "audit";
            case ERROR -> "errors";
        };
        String date = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString();
        return directory.resolve(prefix + "_" + date + ".jsonl");
    }

    private java.util.Optional<LocalDate> eventFileDate(Path file) {
        String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int separator = filename.lastIndexOf('_');
        if (separator < 0 || !filename.endsWith(".jsonl")) {
            return java.util.Optional.empty();
        }
        String date = filename.substring(separator + 1, filename.length() - ".jsonl".length());
        try {
            return java.util.Optional.of(LocalDate.parse(date));
        } catch (DateTimeParseException exc) {
            return java.util.Optional.empty();
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Retention should not interrupt the application.
        }
    }
}
