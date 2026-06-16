package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

public record TelegramAlertsConfig(
    String mode,
    @JsonProperty("signal_dedupe_ttl") String signalDedupeTtl
) {
    private static final String DEFAULT_MODE = "key_events";
    private static final Duration DEFAULT_SIGNAL_DEDUPE_TTL = Duration.ofMinutes(30);

    public TelegramAlertsConfig {
        mode = mode == null || mode.isBlank() ? DEFAULT_MODE : mode;
        signalDedupeTtl = signalDedupeTtl == null || signalDedupeTtl.isBlank()
            ? "30m"
            : signalDedupeTtl;
    }

    public Duration signalDedupeTtlDuration() {
        return parseDuration(signalDedupeTtl, DEFAULT_SIGNAL_DEDUPE_TTL);
    }

    public static TelegramAlertsConfig defaults() {
        return new TelegramAlertsConfig(DEFAULT_MODE, "30m");
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.strip().toLowerCase();
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
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
