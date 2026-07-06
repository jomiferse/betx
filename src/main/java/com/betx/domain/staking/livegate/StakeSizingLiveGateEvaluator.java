package com.betx.domain.staking.livegate;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure deterministic evaluator for future stake sizing live eligibility. */
public class StakeSizingLiveGateEvaluator {
    public StakeSizingLiveGateDecision evaluate(StakeSizingLiveGateContext context) {
        StakeSizingLiveGateContext effectiveContext = context == null ? new StakeSizingLiveGateContext(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) : context;
        List<StakeSizingLiveGateReason> reasons = new ArrayList<>();

        evaluateConfig(effectiveContext.config(), reasons);
        evaluatePolicy(effectiveContext.config(), effectiveContext.policy(), reasons);
        evaluateSample(effectiveContext.config(), effectiveContext.sample(), reasons);
        evaluateHealth(effectiveContext.health(), reasons);
        evaluateBudget(effectiveContext.config(), effectiveContext.budget(), effectiveContext.stake(), reasons);
        evaluateExposure(effectiveContext.exposure(), reasons);
        evaluateStake(effectiveContext.config(), effectiveContext.stake(), reasons);
        evaluateKillSwitch(effectiveContext.killSwitchState(), reasons);
        evaluateManualConfirmation(effectiveContext.config(), reasons);

        boolean gatePassed = reasons.isEmpty();
        return new StakeSizingLiveGateDecision(
            gatePassed ? StakeSizingLiveGateStatus.PASS : StakeSizingLiveGateStatus.FAIL,
            gatePassed,
            gatePassed,
            false,
            false,
            !gatePassed,
            effectiveContext.config().fallbackStake(),
            effectiveContext.policy().candidatePolicy(),
            effectiveContext.policy().candidateRiskProfile(),
            reasons,
            List.of(),
            gatePassed
                ? StakeSizingLiveGateSelectedStakeMode.LIVE_CANDIDATE_NOT_APPLIED
                : StakeSizingLiveGateSelectedStakeMode.FIXED_FALLBACK
        );
    }

    private static void evaluateConfig(
        StakeSizingLiveGateConfig config,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (!config.stakingEnabled()) {
            reasons.add(StakeSizingLiveGateReason.STAKING_DISABLED);
        }
        if (!config.liveEnabled()) {
            reasons.add(StakeSizingLiveGateReason.LIVE_STAKING_DISABLED);
        }
        if (!config.shadowEnabled()) {
            reasons.add(StakeSizingLiveGateReason.SHADOW_DISABLED);
        }
    }

    private static void evaluatePolicy(
        StakeSizingLiveGateConfig config,
        StakeSizingLiveGatePolicy policy,
        List<StakeSizingLiveGateReason> reasons
    ) {
        StakeSizingMode candidatePolicy = policy.candidatePolicy();
        StakeSizingRiskProfile candidateRiskProfile = policy.candidateRiskProfile();
        if (!config.allowedPolicies().contains(candidatePolicy)) {
            reasons.add(StakeSizingLiveGateReason.POLICY_NOT_ALLOWED);
        }
        if (candidatePolicy != StakeSizingMode.RISK_ADJUSTED) {
            reasons.add(StakeSizingLiveGateReason.POLICY_NOT_ALLOWED);
        }
        if (!config.allowedRiskProfiles().contains(candidateRiskProfile)) {
            reasons.add(StakeSizingLiveGateReason.RISK_PROFILE_NOT_ALLOWED);
        }
        if (candidateRiskProfile != StakeSizingRiskProfile.CONSERVATIVE) {
            reasons.add(StakeSizingLiveGateReason.RISK_PROFILE_NOT_ALLOWED);
        }
        if (config.deniedPolicies().contains(candidatePolicy)) {
            reasons.add(StakeSizingLiveGateReason.POLICY_DENIED);
        }
        if (config.deniedRiskProfiles().contains(candidateRiskProfile)) {
            reasons.add(StakeSizingLiveGateReason.RISK_PROFILE_DENIED);
        }
        if (candidatePolicy == StakeSizingMode.FRACTIONAL_KELLY_SHADOW) {
            reasons.add(StakeSizingLiveGateReason.KELLY_NOT_ALLOWED_LIVE);
        }
        if (candidatePolicy == StakeSizingMode.TIERED_CONFIDENCE) {
            reasons.add(StakeSizingLiveGateReason.TIERED_CONFIDENCE_NOT_ALLOWED_LIVE);
        }
        if (candidateRiskProfile == StakeSizingRiskProfile.AGGRESSIVE) {
            reasons.add(StakeSizingLiveGateReason.AGGRESSIVE_PROFILE_NOT_ALLOWED_LIVE);
        }
    }

    private static void evaluateSample(
        StakeSizingLiveGateConfig config,
        StakeSizingLiveGateSample sample,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (sample.realSettledJoined() < config.minSettledJoinedRequired()) {
            reasons.add(StakeSizingLiveGateReason.INSUFFICIENT_SAMPLE_FOR_LIVE_STAKING);
        }
    }

    private static void evaluateHealth(
        StakeSizingLiveGateHealth health,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (health.shadowFailedCount() > 0) {
            reasons.add(StakeSizingLiveGateReason.SHADOW_FAILURES_PRESENT);
        }
        if (health.duplicateLogicalKeysCount() > 0) {
            reasons.add(StakeSizingLiveGateReason.DUPLICATE_LOGICAL_KEYS_PRESENT);
        }
        if (health.forbiddenLiveEventsCount() > 0) {
            reasons.add(StakeSizingLiveGateReason.FORBIDDEN_LIVE_EVENTS_PRESENT);
        }
        if (!health.shadowDiagnosticsFresh()) {
            reasons.add(StakeSizingLiveGateReason.SHADOW_DIAGNOSTICS_STALE);
        }
    }

    private static void evaluateBudget(
        StakeSizingLiveGateConfig config,
        StakeSizingLiveGateBudget budget,
        StakeSizingLiveGateStake stake,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (!budget.budgetSnapshotAvailable()) {
            reasons.add(StakeSizingLiveGateReason.BUDGET_SNAPSHOT_MISSING);
        }
        if (budget.currentDrawdown().compareTo(config.maxAllowedDrawdown()) > 0) {
            reasons.add(StakeSizingLiveGateReason.DRAWDOWN_LIMIT_EXCEEDED);
        }
        if (budget.dailyLossBudgetRemaining().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add(StakeSizingLiveGateReason.DAILY_LOSS_BUDGET_EXHAUSTED);
        }
        if (budget.totalExposureRemaining().compareTo(stake.finalStake()) < 0) {
            reasons.add(StakeSizingLiveGateReason.TOTAL_EXPOSURE_BUDGET_EXHAUSTED);
        }
        if (budget.marketExposureRemaining().compareTo(stake.finalStake()) < 0) {
            reasons.add(StakeSizingLiveGateReason.MARKET_EXPOSURE_BUDGET_EXHAUSTED);
        }
    }

    private static void evaluateExposure(
        StakeSizingLiveGateExposure exposure,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (!exposure.exposureSnapshotAvailable()) {
            reasons.add(StakeSizingLiveGateReason.EXPOSURE_SNAPSHOT_MISSING);
        }
        if (exposure.openPositionsRemaining() <= 0) {
            reasons.add(StakeSizingLiveGateReason.OPEN_POSITIONS_LIMIT_EXHAUSTED);
        }
    }

    private static void evaluateStake(
        StakeSizingLiveGateConfig config,
        StakeSizingLiveGateStake stake,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (stake.finalStake().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add(StakeSizingLiveGateReason.INVALID_FINAL_STAKE);
        }
        if (stake.finalStake().compareTo(stake.minStake()) < 0) {
            reasons.add(StakeSizingLiveGateReason.FINAL_STAKE_BELOW_MIN_STAKE);
        }
        if (stake.finalStake().compareTo(stake.maxStake()) > 0) {
            reasons.add(StakeSizingLiveGateReason.FINAL_STAKE_ABOVE_MAX_STAKE);
        }
        BigDecimal bankrollCap = stake.bankroll().multiply(config.maxSingleStakePctBankroll());
        if (stake.finalStake().compareTo(bankrollCap) > 0) {
            reasons.add(StakeSizingLiveGateReason.FINAL_STAKE_ABOVE_BANKROLL_CAP);
        }
        if (stake.wouldBlock()) {
            reasons.add(StakeSizingLiveGateReason.STAKE_SIZING_DECISION_WOULD_BLOCK);
        }
    }

    private static void evaluateKillSwitch(
        StakeSizingLiveGateKillSwitchState killSwitchState,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (killSwitchState.killSwitchActive()) {
            reasons.add(StakeSizingLiveGateReason.KILL_SWITCH_ACTIVE);
        }
        if (killSwitchState.stakeMismatchActive()) {
            reasons.add(StakeSizingLiveGateReason.STAKE_MISMATCH_ACTIVE);
        }
    }

    private static void evaluateManualConfirmation(
        StakeSizingLiveGateConfig config,
        List<StakeSizingLiveGateReason> reasons
    ) {
        if (config.manualConfirmationRequired() && !config.manualConfirmationAvailable()) {
            reasons.add(StakeSizingLiveGateReason.MANUAL_CONFIRMATION_REQUIRED);
        }
    }
}
