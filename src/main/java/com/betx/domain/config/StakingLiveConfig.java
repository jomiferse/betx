package com.betx.domain.config;

/** Future live stake sizing switch. Disabled by default and not applied in current runtime. */
public record StakingLiveConfig(Boolean enabled) {
    public StakingLiveConfig {
        enabled = enabled != null && enabled;
    }

    public static StakingLiveConfig defaults() {
        return new StakingLiveConfig(false);
    }
}
