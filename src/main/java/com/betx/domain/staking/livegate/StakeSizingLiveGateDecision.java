package com.betx.domain.staking.livegate;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the pure live gate evaluation.
 *
 * <p>{@code conceptuallyEligibleForLive} is diagnostics/design information only. In 3.3.6
 * {@code shouldApplyLive} and {@code officiallyApplied} are always false, even when the gate passes.
 */
public record StakeSizingLiveGateDecision(
    StakeSizingLiveGateStatus status,
    boolean gatePassed,
    boolean conceptuallyEligibleForLive,
    boolean shouldApplyLive,
    boolean officiallyApplied,
    boolean fallbackApplied,
    BigDecimal fallbackStake,
    StakeSizingMode candidatePolicy,
    StakeSizingRiskProfile candidateRiskProfile,
    List<StakeSizingLiveGateReason> reasons,
    List<String> warnings,
    StakeSizingLiveGateSelectedStakeMode selectedStakeMode
) {
    public StakeSizingLiveGateDecision {
        status = status == null ? StakeSizingLiveGateStatus.FAIL : status;
        fallbackStake = fallbackStake == null ? BigDecimal.ZERO : fallbackStake;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        shouldApplyLive = false;
        officiallyApplied = false;
        selectedStakeMode = selectedStakeMode == null
            ? StakeSizingLiveGateSelectedStakeMode.FIXED_FALLBACK
            : selectedStakeMode;
    }
}
