package com.betx.domain.config;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Stake sizing configuration. Live staking remains disabled by default. */
public record StakingConfig(
    Boolean enabled,
    @JsonProperty("shadow_enabled") Boolean shadowEnabled,
    StakeSizingMode mode,
    @JsonProperty("base_stake") BigDecimal baseStake,
    @JsonProperty("min_stake") BigDecimal minStake,
    @JsonProperty("max_stake") BigDecimal maxStake,
    BigDecimal bankroll,
    @JsonProperty("risk_profile") StakeSizingRiskProfile riskProfile,
    StakingLimitsConfig limits,
    StakingShadowConfig shadow,
    StakingLiveConfig live,
    @JsonProperty("dry_run_live_gate") StakingDryRunLiveGateConfig dryRunLiveGate
) {
    public StakingConfig {
        enabled = enabled != null && enabled;
        shadowEnabled = shadowEnabled == null || shadowEnabled;
        mode = mode == null ? StakeSizingMode.FLAT : mode;
        baseStake = baseStake == null ? BigDecimal.ONE : baseStake;
        minStake = minStake == null ? BigDecimal.ONE : minStake;
        maxStake = maxStake == null ? BigDecimal.TEN : maxStake;
        bankroll = bankroll == null ? BigDecimal.valueOf(500) : bankroll;
        riskProfile = riskProfile == null ? StakeSizingRiskProfile.CONSERVATIVE : riskProfile;
        limits = limits == null ? StakingLimitsConfig.defaults() : limits;
        shadow = shadow == null ? StakingShadowConfig.defaults() : shadow;
        live = live == null ? StakingLiveConfig.defaults() : live;
        dryRunLiveGate = dryRunLiveGate == null ? StakingDryRunLiveGateConfig.defaults() : dryRunLiveGate;
    }

    public StakingConfig(
        Boolean enabled,
        Boolean shadowEnabled,
        StakeSizingMode mode,
        BigDecimal baseStake,
        BigDecimal minStake,
        BigDecimal maxStake,
        BigDecimal bankroll,
        StakeSizingRiskProfile riskProfile,
        StakingLimitsConfig limits,
        StakingShadowConfig shadow
    ) {
        this(enabled, shadowEnabled, mode, baseStake, minStake, maxStake, bankroll, riskProfile, limits, shadow, null, null);
    }

    public static StakingConfig defaults() {
        return new StakingConfig(false, true, null, null, null, null, null, null, null, null, null, null);
    }
}
