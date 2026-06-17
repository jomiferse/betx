package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Locale;

/** Configuration for read-only prospective paper trading. */
public final class PaperConfig {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(60);
    private static final int DEFAULT_CLOSING_CAPTURE_MINUTES_BEFORE_START = 2;
    private static final Duration DEFAULT_SETTLEMENT_POLL_INTERVAL = Duration.ofMinutes(5);

    private final boolean continuous;
    private final Duration pollInterval;
    private final int closingCaptureMinutesBeforeStart;
    private final Duration settlementPollInterval;
    private final PaperReadinessGateConfig readinessGate;

    public PaperConfig(
        Boolean continuous,
        String pollInterval,
        Integer closingCaptureMinutesBeforeStart,
        String settlementPollInterval
    ) {
        this(continuous, pollInterval, closingCaptureMinutesBeforeStart, settlementPollInterval, null);
    }

    @JsonCreator
    public PaperConfig(
        @JsonProperty("continuous") Boolean continuous,
        @JsonProperty("poll_interval") String pollInterval,
        @JsonProperty("closing_capture_minutes_before_start") Integer closingCaptureMinutesBeforeStart,
        @JsonProperty("settlement_poll_interval") String settlementPollInterval,
        @JsonProperty("readiness_gate") PaperReadinessGateConfig readinessGate
    ) {
        this.continuous = continuous != null && continuous;
        this.pollInterval = parseDuration(pollInterval, DEFAULT_POLL_INTERVAL);
        this.closingCaptureMinutesBeforeStart = closingCaptureMinutesBeforeStart == null
            ? DEFAULT_CLOSING_CAPTURE_MINUTES_BEFORE_START
            : closingCaptureMinutesBeforeStart;
        this.settlementPollInterval = parseDuration(settlementPollInterval, DEFAULT_SETTLEMENT_POLL_INTERVAL);
        this.readinessGate = readinessGate == null ? PaperReadinessGateConfig.defaults() : readinessGate;
    }

    public static PaperConfig defaults() {
        return new PaperConfig(false, null, null, null);
    }

    public boolean continuous() {
        return continuous;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public int closingCaptureMinutesBeforeStart() {
        return closingCaptureMinutesBeforeStart;
    }

    public Duration settlementPollInterval() {
        return settlementPollInterval;
    }

    public PaperReadinessGateConfig readinessGate() {
        return readinessGate;
    }

    public static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            return Duration.parse(value);
        } catch (RuntimeException exc) {
            throw new IllegalArgumentException("paper duration must use ms, s, m, h or ISO-8601 format: " + value, exc);
        }
    }
}
