package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum IntelligenceAutoBettingPolicy {
    STRICT_APPROVE("strict_approve"),
    BLOCK_ONLY_ON_REJECT("block_only_on_reject");

    private final String configValue;

    IntelligenceAutoBettingPolicy(String configValue) {
        this.configValue = configValue;
    }

    @JsonCreator
    public static IntelligenceAutoBettingPolicy fromConfigValue(String value) {
        if (value == null || value.isBlank()) {
            return STRICT_APPROVE;
        }
        return Arrays.stream(values())
            .filter(policy -> policy.configValue.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "intelligence.auto_betting_policy must be one of: strict_approve, block_only_on_reject."
            ));
    }

    @JsonValue
    public String configValue() {
        return configValue;
    }
}
