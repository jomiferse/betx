package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorageConfig(
    String type,
    String path,
    @JsonProperty("cleanup_market_snapshots_enabled") Boolean cleanupMarketSnapshotsEnabled,
    @JsonProperty("market_snapshot_retention_hours") Integer marketSnapshotRetentionHours
) {
    public StorageConfig {
        type = type == null || type.isBlank() ? "sqlite" : type;
        path = path == null || path.isBlank() ? "./data/betx.db" : path;
        cleanupMarketSnapshotsEnabled = cleanupMarketSnapshotsEnabled == null || cleanupMarketSnapshotsEnabled;
        marketSnapshotRetentionHours = marketSnapshotRetentionHours == null || marketSnapshotRetentionHours <= 0
            ? 48
            : marketSnapshotRetentionHours;
    }

    public StorageConfig(String type, String path) {
        this(type, path, null, null);
    }
}
