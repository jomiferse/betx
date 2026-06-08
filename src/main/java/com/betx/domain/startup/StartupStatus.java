package com.betx.domain.startup;

public record StartupStatus(
    boolean telegramEnabled,
    boolean mlEnabled,
    boolean autoBettingEnabled,
    boolean requestConfirmation,
    String storagePath,
    int pollIntervalSeconds
) {
}
