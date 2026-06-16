package com.betx.domain.config;

import java.time.Duration;
import java.util.Locale;

final class DurationParser {
    private DurationParser() {
    }

    static Duration parse(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
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
            return Duration.parse(value.strip());
        } catch (RuntimeException exc) {
            throw new IllegalArgumentException("Invalid duration: " + value, exc);
        }
    }
}
