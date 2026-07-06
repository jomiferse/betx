package com.betx.domain.staking.livegate;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import java.math.BigDecimal;
import java.util.Set;

/** Static inputs controlling future live gate eligibility. */
public record StakeSizingLiveGateConfig(
    boolean stakingEnabled,
    boolean liveEnabled,
    boolean shadowEnabled,
    Set<StakeSizingMode> allowedPolicies,
    Set<StakeSizingRiskProfile> allowedRiskProfiles,
    Set<StakeSizingMode> deniedPolicies,
    Set<StakeSizingRiskProfile> deniedRiskProfiles,
    int minSettledJoinedRequired,
    BigDecimal maxAllowedDrawdown,
    BigDecimal maxSingleStakePctBankroll,
    boolean manualConfirmationRequired,
    boolean manualConfirmationAvailable,
    BigDecimal fallbackStake
) {
    public StakeSizingLiveGateConfig {
        allowedPolicies = allowedPolicies == null ? Set.of() : Set.copyOf(allowedPolicies);
        allowedRiskProfiles = allowedRiskProfiles == null ? Set.of() : Set.copyOf(allowedRiskProfiles);
        deniedPolicies = deniedPolicies == null ? Set.of() : Set.copyOf(deniedPolicies);
        deniedRiskProfiles = deniedRiskProfiles == null ? Set.of() : Set.copyOf(deniedRiskProfiles);
        minSettledJoinedRequired = Math.max(0, minSettledJoinedRequired);
        maxAllowedDrawdown = valueOrZero(maxAllowedDrawdown);
        maxSingleStakePctBankroll = valueOrZero(maxSingleStakePctBankroll);
        fallbackStake = valueOrZero(fallbackStake);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
