package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(
    String mode,
    @JsonProperty("log_level") String logLevel
) {
    public AppConfig {
        mode = mode == null || mode.isBlank() ? "dry-run" : mode;
        logLevel = logLevel == null || logLevel.isBlank() ? "info" : logLevel;
    }

    public AppConfig withMode(String newMode) {
        return new AppConfig(newMode, logLevel);
    }
}
