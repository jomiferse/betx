package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MlConfig(
    Boolean enabled,
    @JsonProperty("model_path") String modelPath,
    @JsonProperty("min_confidence") BigDecimal minConfidence
) {
    public MlConfig {
        enabled = enabled != null && enabled;
        modelPath = modelPath == null || modelPath.isBlank() ? "./models/value_model.pkl" : modelPath;
        minConfidence = minConfidence == null ? BigDecimal.valueOf(0.70) : minConfidence;
    }
}
