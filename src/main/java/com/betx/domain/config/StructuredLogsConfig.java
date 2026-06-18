package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StructuredLogsConfig(
    Boolean enabled,
    String directory,
    @JsonProperty("retention_days") Integer retentionDays
) {
    public StructuredLogsConfig {
        enabled = enabled == null || enabled;
        directory = directory == null || directory.isBlank() ? "./logs/events" : directory;
        retentionDays = retentionDays == null ? 30 : retentionDays;
    }
}
