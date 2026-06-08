package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(
    @JsonProperty("log_level") String logLevel
) {
    public AppConfig {
        logLevel = logLevel == null || logLevel.isBlank() ? "info" : logLevel;
    }
}
