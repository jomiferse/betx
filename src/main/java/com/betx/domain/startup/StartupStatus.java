package com.betx.domain.startup;

public record StartupStatus(
    String mode,
    boolean telegramEnabled,
    boolean mlEnabled,
    boolean liveBettingEnabled,
    String storagePath,
    int pollIntervalSeconds
) {
}
