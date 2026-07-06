package com.betx.domain.staking.livegate;

/** Describes how a future stake would be selected after evaluating the live gate. */
public enum StakeSizingLiveGateSelectedStakeMode {
    FIXED_FALLBACK,
    LIVE_CANDIDATE_NOT_APPLIED,
    LIVE_ALLOWED_CONCEPTUALLY
}
