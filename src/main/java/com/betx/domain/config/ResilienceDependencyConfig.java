package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

public record ResilienceDependencyConfig(
    @JsonProperty("failure_threshold") Integer failureThreshold,
    String cooldown
) {
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(5);

    public ResilienceDependencyConfig {
        failureThreshold = failureThreshold == null || failureThreshold <= 0 ? DEFAULT_FAILURE_THRESHOLD : failureThreshold;
        cooldown = cooldown == null || cooldown.isBlank() ? "5m" : cooldown;
    }

    public Duration cooldownDuration() {
        return parseDuration(cooldown, DEFAULT_COOLDOWN);
    }

    public static ResilienceDependencyConfig defaults() {
        return new ResilienceDependencyConfig(DEFAULT_FAILURE_THRESHOLD, "5m");
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.strip().toLowerCase();
        try {
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
