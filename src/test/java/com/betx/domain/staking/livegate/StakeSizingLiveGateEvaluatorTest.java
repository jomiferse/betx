package com.betx.domain.staking.livegate;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StakeSizingLiveGateEvaluatorTest {
    private final StakeSizingLiveGateEvaluator evaluator = new StakeSizingLiveGateEvaluator();

    @Test
    void failsWhenStakingIsDisabled() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().stakingEnabled(false).build());

        assertFail(decision, StakeSizingLiveGateReason.STAKING_DISABLED);
    }

    @Test
    void failsWhenLiveStakingIsDisabled() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().liveEnabled(false).build());

        assertFail(decision, StakeSizingLiveGateReason.LIVE_STAKING_DISABLED);
    }

    @Test
    void failsWhenShadowIsDisabledBecauseLiveGateRequiresShadowEvidence() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().shadowEnabled(false).build());

        assertFail(decision, StakeSizingLiveGateReason.SHADOW_DISABLED);
    }

    @Test
    void allowsRiskAdjustedConservativeConceptuallyWhenAllGatesPass() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().build());

        assertThat(decision.status()).isEqualTo(StakeSizingLiveGateStatus.PASS);
        assertThat(decision.gatePassed()).isTrue();
        assertThat(decision.conceptuallyEligibleForLive()).isTrue();
        assertThat(decision.reasons()).isEmpty();
        assertThat(decision.fallbackApplied()).isFalse();
        assertThat(decision.selectedStakeMode()).isEqualTo(StakeSizingLiveGateSelectedStakeMode.LIVE_CANDIDATE_NOT_APPLIED);
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    @Test
    void blocksKellyForLiveEvenIfAllowlisted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidatePolicy(StakeSizingMode.FRACTIONAL_KELLY_SHADOW)
            .allowedPolicies(Set.of(StakeSizingMode.FRACTIONAL_KELLY_SHADOW))
            .deniedPolicies(Set.of())
            .build());

        assertFail(decision, StakeSizingLiveGateReason.KELLY_NOT_ALLOWED_LIVE);
    }

    @Test
    void blocksTieredConfidenceForLiveEvenIfAllowlisted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidatePolicy(StakeSizingMode.TIERED_CONFIDENCE)
            .allowedPolicies(Set.of(StakeSizingMode.TIERED_CONFIDENCE))
            .deniedPolicies(Set.of())
            .build());

        assertFail(decision, StakeSizingLiveGateReason.TIERED_CONFIDENCE_NOT_ALLOWED_LIVE);
    }

    @Test
    void blocksAggressiveRiskProfileForLiveEvenIfAllowlisted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidateRiskProfile(StakeSizingRiskProfile.AGGRESSIVE)
            .allowedRiskProfiles(Set.of(StakeSizingRiskProfile.AGGRESSIVE))
            .deniedRiskProfiles(Set.of())
            .build());

        assertFail(decision, StakeSizingLiveGateReason.AGGRESSIVE_PROFILE_NOT_ALLOWED_LIVE);
    }

    @Test
    void failsWhenPolicyIsNotAllowlisted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidatePolicy(StakeSizingMode.FLAT)
            .build());

        assertFail(decision, StakeSizingLiveGateReason.POLICY_NOT_ALLOWED);
    }

    @Test
    void blocksFlatEvenIfItIsAllowlistedBecauseInitialLiveCandidateIsRiskAdjustedOnly() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidatePolicy(StakeSizingMode.FLAT)
            .allowedPolicies(Set.of(StakeSizingMode.FLAT))
            .build());

        assertFail(decision, StakeSizingLiveGateReason.POLICY_NOT_ALLOWED);
    }

    @Test
    void failsWhenRiskProfileIsNotAllowlisted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidateRiskProfile(StakeSizingRiskProfile.BALANCED)
            .build());

        assertFail(decision, StakeSizingLiveGateReason.RISK_PROFILE_NOT_ALLOWED);
    }

    @Test
    void blocksBalancedEvenIfItIsAllowlistedBecauseInitialLiveCandidateIsConservativeOnly() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidateRiskProfile(StakeSizingRiskProfile.BALANCED)
            .allowedRiskProfiles(Set.of(StakeSizingRiskProfile.BALANCED))
            .build());

        assertFail(decision, StakeSizingLiveGateReason.RISK_PROFILE_NOT_ALLOWED);
    }

    @Test
    void failsWhenPolicyIsDenied() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .candidatePolicy(StakeSizingMode.FLAT)
            .allowedPolicies(Set.of(StakeSizingMode.FLAT))
            .deniedPolicies(Set.of(StakeSizingMode.FLAT))
            .build());

        assertFail(decision, StakeSizingLiveGateReason.POLICY_DENIED);
    }

    @Test
    void failsWhenRiskProfileIsDenied() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .deniedRiskProfiles(Set.of(StakeSizingRiskProfile.CONSERVATIVE))
            .build());

        assertFail(decision, StakeSizingLiveGateReason.RISK_PROFILE_DENIED);
    }

    @Test
    void failsCurrentSampleOfFiftyOneAgainstOperationalMinimumOfOneHundred() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().realSettledJoined(51).build());

        assertFail(decision, StakeSizingLiveGateReason.INSUFFICIENT_SAMPLE_FOR_LIVE_STAKING);
    }

    @Test
    void conceptuallyPassesAtOneHundredSettledJoinedWhenNoOtherGateFails() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().realSettledJoined(100).build());

        assertThat(decision.gatePassed()).isTrue();
        assertThat(decision.conceptuallyEligibleForLive()).isTrue();
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    @Test
    void failsWhenShadowFailuresArePresent() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().shadowFailedCount(1).build());

        assertFail(decision, StakeSizingLiveGateReason.SHADOW_FAILURES_PRESENT);
    }

    @Test
    void failsWhenDuplicateLogicalKeysArePresent() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().duplicateLogicalKeysCount(1).build());

        assertFail(decision, StakeSizingLiveGateReason.DUPLICATE_LOGICAL_KEYS_PRESENT);
    }

    @Test
    void failsWhenForbiddenLiveEventsArePresent() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().forbiddenLiveEventsCount(1).build());

        assertFail(decision, StakeSizingLiveGateReason.FORBIDDEN_LIVE_EVENTS_PRESENT);
    }

    @Test
    void failsWhenShadowDiagnosticsAreStale() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().shadowDiagnosticsFresh(false).build());

        assertFail(decision, StakeSizingLiveGateReason.SHADOW_DIAGNOSTICS_STALE);
    }

    @Test
    void failsWhenCurrentDrawdownExceedsLimit() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().currentDrawdown("30.00").build());

        assertFail(decision, StakeSizingLiveGateReason.DRAWDOWN_LIMIT_EXCEEDED);
    }

    @Test
    void failsWhenDailyLossBudgetIsExhausted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().dailyLossBudgetRemaining("0.00").build());

        assertFail(decision, StakeSizingLiveGateReason.DAILY_LOSS_BUDGET_EXHAUSTED);
    }

    @Test
    void failsWhenTotalExposureBudgetCannotCoverFinalStake() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().totalExposureRemaining("2.99").build());

        assertFail(decision, StakeSizingLiveGateReason.TOTAL_EXPOSURE_BUDGET_EXHAUSTED);
    }

    @Test
    void failsWhenMarketExposureBudgetCannotCoverFinalStake() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().marketExposureRemaining("2.99").build());

        assertFail(decision, StakeSizingLiveGateReason.MARKET_EXPOSURE_BUDGET_EXHAUSTED);
    }

    @Test
    void failsWhenOpenPositionsAreExhausted() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().openPositionsRemaining(0).build());

        assertFail(decision, StakeSizingLiveGateReason.OPEN_POSITIONS_LIMIT_EXHAUSTED);
    }

    @Test
    void failsWhenBudgetSnapshotIsMissing() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().budgetSnapshotAvailable(false).build());

        assertFail(decision, StakeSizingLiveGateReason.BUDGET_SNAPSHOT_MISSING);
    }

    @Test
    void failsWhenExposureSnapshotIsMissing() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().exposureSnapshotAvailable(false).build());

        assertFail(decision, StakeSizingLiveGateReason.EXPOSURE_SNAPSHOT_MISSING);
    }

    @Test
    void failsWhenFinalStakeIsNotPositive() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().finalStake("0.00").build());

        assertFail(decision, StakeSizingLiveGateReason.INVALID_FINAL_STAKE);
    }

    @Test
    void failsWhenFinalStakeIsBelowMinimumStake() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().finalStake("0.50").build());

        assertFail(decision, StakeSizingLiveGateReason.FINAL_STAKE_BELOW_MIN_STAKE);
    }

    @Test
    void failsWhenFinalStakeExceedsMaximumStake() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().finalStake("6.00").build());

        assertFail(decision, StakeSizingLiveGateReason.FINAL_STAKE_ABOVE_MAX_STAKE);
    }

    @Test
    void failsWhenFinalStakeExceedsBankrollCap() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().finalStake("5.01").build());

        assertFail(decision, StakeSizingLiveGateReason.FINAL_STAKE_ABOVE_BANKROLL_CAP);
    }

    @Test
    void treatsMaxStakeAsCapNotDefaultStake() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .finalStake("3.00")
            .maxStake("5.00")
            .build());

        assertThat(decision.gatePassed()).isTrue();
        assertThat(decision.reasons()).doesNotContain(StakeSizingLiveGateReason.FINAL_STAKE_ABOVE_MAX_STAKE);
        assertThat(decision.selectedStakeMode()).isEqualTo(StakeSizingLiveGateSelectedStakeMode.LIVE_CANDIDATE_NOT_APPLIED);
    }

    @Test
    void failsWhenKillSwitchIsActive() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().killSwitchActive(true).build());

        assertFail(decision, StakeSizingLiveGateReason.KILL_SWITCH_ACTIVE);
    }

    @Test
    void failsWhenStakeMismatchIsActive() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().stakeMismatchActive(true).build());

        assertFail(decision, StakeSizingLiveGateReason.STAKE_MISMATCH_ACTIVE);
    }

    @Test
    void failsWhenStakeSizingDecisionWouldBlock() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().wouldBlock(true).build());

        assertFail(decision, StakeSizingLiveGateReason.STAKE_SIZING_DECISION_WOULD_BLOCK);
    }

    @Test
    void failsWhenManualConfirmationIsRequiredButUnavailable() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .manualConfirmationRequired(true)
            .manualConfirmationAvailable(false)
            .build());

        assertFail(decision, StakeSizingLiveGateReason.MANUAL_CONFIRMATION_REQUIRED);
    }

    @Test
    void canPassConceptuallyWhenManualConfirmationIsRequiredAndAvailable() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .manualConfirmationRequired(true)
            .manualConfirmationAvailable(true)
            .build());

        assertThat(decision.gatePassed()).isTrue();
        assertThat(decision.conceptuallyEligibleForLive()).isTrue();
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    @Test
    void gateFailureAlwaysReturnsFixedFallbackStakeWithoutLiveApplication() {
        StakeSizingLiveGateDecision decision = evaluate(validContext().liveEnabled(false).build());

        assertThat(decision.fallbackApplied()).isTrue();
        assertThat(decision.fallbackStake()).isEqualByComparingTo("1.00");
        assertThat(decision.selectedStakeMode()).isEqualTo(StakeSizingLiveGateSelectedStakeMode.FIXED_FALLBACK);
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    @Test
    void reportsMultipleFailuresRatherThanOnlyTheFirstFailure() {
        StakeSizingLiveGateDecision decision = evaluate(validContext()
            .stakingEnabled(false)
            .liveEnabled(false)
            .realSettledJoined(51)
            .shadowFailedCount(1)
            .build());

        assertThat(decision.reasons()).contains(
            StakeSizingLiveGateReason.STAKING_DISABLED,
            StakeSizingLiveGateReason.LIVE_STAKING_DISABLED,
            StakeSizingLiveGateReason.INSUFFICIENT_SAMPLE_FOR_LIVE_STAKING,
            StakeSizingLiveGateReason.SHADOW_FAILURES_PRESENT
        );
        assertThat(decision.gatePassed()).isFalse();
        assertThat(decision.fallbackApplied()).isTrue();
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    private StakeSizingLiveGateDecision evaluate(StakeSizingLiveGateContext context) {
        return evaluator.evaluate(context);
    }

    private static void assertFail(StakeSizingLiveGateDecision decision, StakeSizingLiveGateReason reason) {
        assertThat(decision.status()).isEqualTo(StakeSizingLiveGateStatus.FAIL);
        assertThat(decision.gatePassed()).isFalse();
        assertThat(decision.conceptuallyEligibleForLive()).isFalse();
        assertThat(decision.reasons()).contains(reason);
        assertThat(decision.fallbackApplied()).isTrue();
        assertThat(decision.fallbackStake()).isEqualByComparingTo("1.00");
        assertThat(decision.selectedStakeMode()).isEqualTo(StakeSizingLiveGateSelectedStakeMode.FIXED_FALLBACK);
        assertThat(decision.shouldApplyLive()).isFalse();
        assertThat(decision.officiallyApplied()).isFalse();
    }

    private static ContextBuilder validContext() {
        return new ContextBuilder();
    }

    private static final class ContextBuilder {
        private boolean stakingEnabled = true;
        private boolean liveEnabled = true;
        private boolean shadowEnabled = true;
        private StakeSizingMode candidatePolicy = StakeSizingMode.RISK_ADJUSTED;
        private StakeSizingRiskProfile candidateRiskProfile = StakeSizingRiskProfile.CONSERVATIVE;
        private Set<StakeSizingMode> allowedPolicies = Set.of(StakeSizingMode.RISK_ADJUSTED);
        private Set<StakeSizingRiskProfile> allowedRiskProfiles = Set.of(StakeSizingRiskProfile.CONSERVATIVE);
        private Set<StakeSizingMode> deniedPolicies = Set.of(
            StakeSizingMode.FRACTIONAL_KELLY_SHADOW,
            StakeSizingMode.TIERED_CONFIDENCE
        );
        private Set<StakeSizingRiskProfile> deniedRiskProfiles = Set.of(StakeSizingRiskProfile.AGGRESSIVE);
        private int minSettledJoinedRequired = 100;
        private int realSettledJoined = 100;
        private long shadowFailedCount = 0;
        private long duplicateLogicalKeysCount = 0;
        private long forbiddenLiveEventsCount = 0;
        private String currentDrawdown = "10.00";
        private String maxAllowedDrawdown = "25.00";
        private String dailyLossBudgetRemaining = "25.00";
        private String totalExposureRemaining = "50.00";
        private String marketExposureRemaining = "5.00";
        private int openPositionsRemaining = 1;
        private String finalStake = "3.00";
        private String minStake = "1.00";
        private String maxStake = "5.00";
        private String bankroll = "500.00";
        private String maxSingleStakePctBankroll = "0.01";
        private boolean wouldBlock = false;
        private boolean killSwitchActive = false;
        private boolean stakeMismatchActive = false;
        private boolean budgetSnapshotAvailable = true;
        private boolean exposureSnapshotAvailable = true;
        private boolean shadowDiagnosticsFresh = true;
        private boolean manualConfirmationRequired = false;
        private boolean manualConfirmationAvailable = false;
        private String fallbackStake = "1.00";

        private ContextBuilder stakingEnabled(boolean value) {
            stakingEnabled = value;
            return this;
        }

        private ContextBuilder liveEnabled(boolean value) {
            liveEnabled = value;
            return this;
        }

        private ContextBuilder shadowEnabled(boolean value) {
            shadowEnabled = value;
            return this;
        }

        private ContextBuilder candidatePolicy(StakeSizingMode value) {
            candidatePolicy = value;
            return this;
        }

        private ContextBuilder candidateRiskProfile(StakeSizingRiskProfile value) {
            candidateRiskProfile = value;
            return this;
        }

        private ContextBuilder allowedPolicies(Set<StakeSizingMode> value) {
            allowedPolicies = value;
            return this;
        }

        private ContextBuilder allowedRiskProfiles(Set<StakeSizingRiskProfile> value) {
            allowedRiskProfiles = value;
            return this;
        }

        private ContextBuilder deniedPolicies(Set<StakeSizingMode> value) {
            deniedPolicies = value;
            return this;
        }

        private ContextBuilder deniedRiskProfiles(Set<StakeSizingRiskProfile> value) {
            deniedRiskProfiles = value;
            return this;
        }

        private ContextBuilder realSettledJoined(int value) {
            realSettledJoined = value;
            return this;
        }

        private ContextBuilder shadowFailedCount(long value) {
            shadowFailedCount = value;
            return this;
        }

        private ContextBuilder duplicateLogicalKeysCount(long value) {
            duplicateLogicalKeysCount = value;
            return this;
        }

        private ContextBuilder forbiddenLiveEventsCount(long value) {
            forbiddenLiveEventsCount = value;
            return this;
        }

        private ContextBuilder shadowDiagnosticsFresh(boolean value) {
            shadowDiagnosticsFresh = value;
            return this;
        }

        private ContextBuilder currentDrawdown(String value) {
            currentDrawdown = value;
            return this;
        }

        private ContextBuilder dailyLossBudgetRemaining(String value) {
            dailyLossBudgetRemaining = value;
            return this;
        }

        private ContextBuilder totalExposureRemaining(String value) {
            totalExposureRemaining = value;
            return this;
        }

        private ContextBuilder marketExposureRemaining(String value) {
            marketExposureRemaining = value;
            return this;
        }

        private ContextBuilder openPositionsRemaining(int value) {
            openPositionsRemaining = value;
            return this;
        }

        private ContextBuilder budgetSnapshotAvailable(boolean value) {
            budgetSnapshotAvailable = value;
            return this;
        }

        private ContextBuilder exposureSnapshotAvailable(boolean value) {
            exposureSnapshotAvailable = value;
            return this;
        }

        private ContextBuilder finalStake(String value) {
            finalStake = value;
            return this;
        }

        private ContextBuilder maxStake(String value) {
            maxStake = value;
            return this;
        }

        private ContextBuilder killSwitchActive(boolean value) {
            killSwitchActive = value;
            return this;
        }

        private ContextBuilder stakeMismatchActive(boolean value) {
            stakeMismatchActive = value;
            return this;
        }

        private ContextBuilder wouldBlock(boolean value) {
            wouldBlock = value;
            return this;
        }

        private ContextBuilder manualConfirmationRequired(boolean value) {
            manualConfirmationRequired = value;
            return this;
        }

        private ContextBuilder manualConfirmationAvailable(boolean value) {
            manualConfirmationAvailable = value;
            return this;
        }

        private StakeSizingLiveGateContext build() {
            return new StakeSizingLiveGateContext(
                new StakeSizingLiveGateConfig(
                    stakingEnabled,
                    liveEnabled,
                    shadowEnabled,
                    allowedPolicies,
                    allowedRiskProfiles,
                    deniedPolicies,
                    deniedRiskProfiles,
                    minSettledJoinedRequired,
                    money(maxAllowedDrawdown),
                    money(maxSingleStakePctBankroll),
                    manualConfirmationRequired,
                    manualConfirmationAvailable,
                    money(fallbackStake)
                ),
                new StakeSizingLiveGatePolicy(candidatePolicy, candidateRiskProfile),
                new StakeSizingLiveGateSample(realSettledJoined),
                new StakeSizingLiveGateHealth(
                    shadowFailedCount,
                    duplicateLogicalKeysCount,
                    forbiddenLiveEventsCount,
                    shadowDiagnosticsFresh
                ),
                new StakeSizingLiveGateBudget(
                    money(currentDrawdown),
                    money(dailyLossBudgetRemaining),
                    money(totalExposureRemaining),
                    money(marketExposureRemaining),
                    budgetSnapshotAvailable
                ),
                new StakeSizingLiveGateExposure(openPositionsRemaining, exposureSnapshotAvailable),
                new StakeSizingLiveGateKillSwitchState(killSwitchActive, stakeMismatchActive),
                new StakeSizingLiveGateStake(
                    money(finalStake),
                    money(minStake),
                    money(maxStake),
                    money(bankroll),
                    wouldBlock
                )
            );
        }

        private static BigDecimal money(String value) {
            return new BigDecimal(value);
        }
    }
}
