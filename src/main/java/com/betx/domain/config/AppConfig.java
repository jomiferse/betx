package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(
    @JsonProperty("log_level") String logLevel,
    @JsonProperty("structured_logs") StructuredLogsConfig structuredLogs
) {
    public AppConfig {
        logLevel = logLevel == null || logLevel.isBlank() ? "info" : logLevel;
        structuredLogs = structuredLogs == null ? new StructuredLogsConfig(null, null, null) : structuredLogs;
    }

    public AppConfig(String logLevel) {
        this(logLevel, null);
    }
}
