package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaperEvaluationsStorageConfig(
    @JsonProperty("detail_retention_days") Integer detailRetentionDays,
    @JsonProperty("rejection_sample_rate") Double rejectionSampleRate
) {
    public PaperEvaluationsStorageConfig {
        detailRetentionDays = detailRetentionDays == null || detailRetentionDays <= 0 ? 7 : detailRetentionDays;
        rejectionSampleRate = rejectionSampleRate == null ? 0.0 : Math.max(0.0, Math.min(1.0, rejectionSampleRate));
    }

    public static PaperEvaluationsStorageConfig defaults() {
        return new PaperEvaluationsStorageConfig(7, 0.0);
    }
}
