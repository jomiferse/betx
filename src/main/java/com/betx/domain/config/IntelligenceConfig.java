package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntelligenceConfig(
    Boolean enabled,
    String provider,
    String model,
    @JsonProperty("api_key_env") String apiKeyEnv,
    @JsonProperty("api_key") String apiKey,
    @JsonProperty("timeout_seconds") Integer timeoutSeconds,
    @JsonProperty("min_confidence") Integer minConfidence,
    @JsonProperty("auto_betting_policy") IntelligenceAutoBettingPolicy autoBettingPolicy
) {
    private static final boolean DEFAULT_ENABLED = false;
    private static final String DEFAULT_PROVIDER = "openrouter";
    private static final String DEFAULT_MODEL = "x-ai/grok-4.3";
    private static final String DEFAULT_API_KEY_ENV = "OPENROUTER_API_KEY";
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final int DEFAULT_MIN_CONFIDENCE = 70;

    public IntelligenceConfig {
        enabled = enabled == null ? DEFAULT_ENABLED : enabled;
        provider = provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        apiKeyEnv = apiKeyEnv == null || apiKeyEnv.isBlank() ? DEFAULT_API_KEY_ENV : apiKeyEnv;
        apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        timeoutSeconds = timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        minConfidence = minConfidence == null ? DEFAULT_MIN_CONFIDENCE : minConfidence;
        autoBettingPolicy = autoBettingPolicy == null ? IntelligenceAutoBettingPolicy.STRICT_APPROVE : autoBettingPolicy;
    }

    public IntelligenceConfig(
        Boolean enabled,
        String provider,
        String model,
        String apiKeyEnv,
        String apiKey,
        Integer timeoutSeconds,
        Integer minConfidence
    ) {
        this(enabled, provider, model, apiKeyEnv, apiKey, timeoutSeconds, minConfidence, null);
    }
}
