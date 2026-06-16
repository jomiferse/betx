package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorageConfig(
    String type,
    String path,
    @JsonProperty("cleanup_market_snapshots_enabled") Boolean cleanupMarketSnapshotsEnabled,
    @JsonProperty("market_snapshot_retention_hours") Integer marketSnapshotRetentionHours,
    @JsonProperty("paper_evaluations") PaperEvaluationsStorageConfig paperEvaluations
) {
    public StorageConfig {
        type = type == null || type.isBlank() ? "sqlite" : type;
        path = path == null || path.isBlank() ? "./data/betx.db" : path;
        cleanupMarketSnapshotsEnabled = cleanupMarketSnapshotsEnabled == null || cleanupMarketSnapshotsEnabled;
        marketSnapshotRetentionHours = marketSnapshotRetentionHours == null || marketSnapshotRetentionHours <= 0
            ? 48
            : marketSnapshotRetentionHours;
        paperEvaluations = paperEvaluations == null ? PaperEvaluationsStorageConfig.defaults() : paperEvaluations;
    }

    public StorageConfig(String type, String path) {
        this(type, path, null, null, null);
    }

    public StorageConfig(
        String type,
        String path,
        Boolean cleanupMarketSnapshotsEnabled,
        Integer marketSnapshotRetentionHours
    ) {
        this(type, path, cleanupMarketSnapshotsEnabled, marketSnapshotRetentionHours, null);
    }
}
