package com.betx.application;

import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.StakingConfig;
import com.betx.domain.config.StakingDryRunLiveGateConfig;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.livegate.StakeSizingLiveGateBudget;
import com.betx.domain.staking.livegate.StakeSizingLiveGateConfig;
import com.betx.domain.staking.livegate.StakeSizingLiveGateContext;
import com.betx.domain.staking.livegate.StakeSizingLiveGateDecision;
import com.betx.domain.staking.livegate.StakeSizingLiveGateEvaluator;
import com.betx.domain.staking.livegate.StakeSizingLiveGateExposure;
import com.betx.domain.staking.livegate.StakeSizingLiveGateHealth;
import com.betx.domain.staking.livegate.StakeSizingLiveGateKillSwitchState;
import com.betx.domain.staking.livegate.StakeSizingLiveGatePolicy;
import com.betx.domain.staking.livegate.StakeSizingLiveGateReason;
import com.betx.domain.staking.livegate.StakeSizingLiveGateSample;
import com.betx.domain.staking.livegate.StakeSizingLiveGateStake;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runtime dry-run validation for future stake sizing live gate behavior. Never applies stake. */
@Service
public class StakeSizingLiveGateDryRunService {
    private static final BigDecimal MIN_STAKE = new BigDecimal("1.00");
    private static final BigDecimal MAX_STAKE = new BigDecimal("5.00");
    private static final BigDecimal BANKROLL = new BigDecimal("500.00");
    private static final BigDecimal MAX_ALLOWED_DRAWDOWN = new BigDecimal("25.00");
    private static final BigDecimal MAX_SINGLE_STAKE_PCT_BANKROLL = new BigDecimal("0.01");
    private static final BigDecimal DAILY_LOSS_BUDGET_REMAINING = new BigDecimal("25.00");
    private static final BigDecimal TOTAL_EXPOSURE_REMAINING = new BigDecimal("50.00");
    private static final BigDecimal MARKET_EXPOSURE_REMAINING = new BigDecimal("5.00");
    private static final int OPEN_POSITIONS_REMAINING = 10;

    private final BetxEventLogger eventLogger;
    private final Clock clock;
    private final StakeSizingLiveGateEvaluator evaluator;

    @Autowired
    public StakeSizingLiveGateDryRunService(BetxEventLogger eventLogger) {
        this(eventLogger, Clock.systemUTC());
    }

    StakeSizingLiveGateDryRunService(BetxEventLogger eventLogger, Clock clock) {
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), clock) : eventLogger;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.evaluator = new StakeSizingLiveGateEvaluator();
    }

    StakeSizingLiveGateDryRunService(
        BetxEventLogger eventLogger,
        Clock clock,
        StakeSizingLiveGateEvaluator evaluator
    ) {
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), clock) : eventLogger;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.evaluator = evaluator == null ? new StakeSizingLiveGateEvaluator() : evaluator;
    }

    public void evaluate(
        BetxConfig config,
        String cycleId,
        BetRecommendation recommendation,
        StakeSizingShadowDecision currentShadowDecision
    ) {
        if (recommendation == null) {
            return;
        }
        BetxConfig effectiveConfig = config == null ? BetxConfig.defaults() : config;
        StakingDryRunLiveGateConfig dryRunConfig = effectiveConfig.staking().dryRunLiveGate();
        if (!dryRunConfig.enabled() || !dryRunConfig.emitLogs()) {
            return;
        }
        try {
            DryRunStake stake = dryRunStake(dryRunConfig, currentShadowDecision);
            StakeSizingLiveGateConfig liveGateConfig = liveGateConfig(effectiveConfig.staking(), dryRunConfig);
            StakeSizingLiveGateHealth health = new StakeSizingLiveGateHealth(0, 0, 0, false);
            StakeSizingLiveGateDecision decision = evaluator.evaluate(new StakeSizingLiveGateContext(
                liveGateConfig,
                new StakeSizingLiveGatePolicy(dryRunConfig.policy(), dryRunConfig.riskProfile()),
                new StakeSizingLiveGateSample(0),
                health,
                new StakeSizingLiveGateBudget(
                    BigDecimal.ZERO,
                    DAILY_LOSS_BUDGET_REMAINING,
                    TOTAL_EXPOSURE_REMAINING,
                    MARKET_EXPOSURE_REMAINING,
                    true
                ),
                new StakeSizingLiveGateExposure(OPEN_POSITIONS_REMAINING, true),
                new StakeSizingLiveGateKillSwitchState(false, false),
                new StakeSizingLiveGateStake(stake.representativeFinalStake(), MIN_STAKE, MAX_STAKE, BANKROLL, false)
            ));
            logEvaluated(cycleId, recommendation, stake, decision, liveGateConfig, health, dryRunConfig);
        } catch (RuntimeException exc) {
            logFailed(cycleId, recommendation, exc);
        }
    }

    private StakeSizingLiveGateConfig liveGateConfig(
        StakingConfig staking,
        StakingDryRunLiveGateConfig dryRunConfig
    ) {
        return new StakeSizingLiveGateConfig(
            false,
            staking.live().enabled(),
            staking.shadowEnabled(),
            EnumSet.of(dryRunConfig.policy()),
            EnumSet.of(dryRunConfig.riskProfile()),
            EnumSet.of(StakeSizingMode.FRACTIONAL_KELLY_SHADOW, StakeSizingMode.TIERED_CONFIDENCE),
            EnumSet.of(StakeSizingRiskProfile.AGGRESSIVE),
            dryRunConfig.minSettledJoinedRequired(),
            MAX_ALLOWED_DRAWDOWN,
            MAX_SINGLE_STAKE_PCT_BANKROLL,
            true,
            false,
            dryRunConfig.fallbackStake()
        );
    }

    private DryRunStake dryRunStake(
        StakingDryRunLiveGateConfig dryRunConfig,
        StakeSizingShadowDecision currentShadowDecision
    ) {
        if (currentShadowDecision == null) {
            return new DryRunStake(
                dryRunConfig.fixedStake(),
                "FALLBACK_NO_CURRENT_SHADOW_DECISION",
                List.of("LIVE_GATE_DRY_RUN_STAKE_DECISION_MISSING")
            );
        }
        if (currentShadowDecision.finalStake() == null || currentShadowDecision.finalStake().compareTo(BigDecimal.ZERO) <= 0) {
            return new DryRunStake(
                dryRunConfig.fixedStake(),
                "FALLBACK_INVALID_CURRENT_SHADOW_DECISION",
                List.of("LIVE_GATE_DRY_RUN_STAKE_DECISION_INVALID")
            );
        }
        return new DryRunStake(currentShadowDecision.finalStake(), "CURRENT_SHADOW_DECISION", List.of());
    }

    private void logEvaluated(
        String cycleId,
        BetRecommendation recommendation,
        DryRunStake stake,
        StakeSizingLiveGateDecision decision,
        StakeSizingLiveGateConfig config,
        StakeSizingLiveGateHealth health,
        StakingDryRunLiveGateConfig dryRunConfig
    ) {
        eventLogger.info(BetxEventCategory.ANALYTICS, "stake_sizing.live_gate_dry_run_evaluated")
            .correlationId("recommendation-" + recommendation.id())
            .cycleId(cycleId)
            .exchange(recommendation.exchange())
            .marketId(recommendation.marketId())
            .selectionId(recommendation.selectionId())
            .strategy(recommendation.strategyName())
            .executionMode("dry_run_live_gate")
            .result(decision.status().name())
            .field("recommendationId", recommendation.id())
            .field("canonicalKey", recommendation.canonicalKey())
            .field("selectionSide", recommendation.selectionSide().name())
            .field("strategyName", recommendation.strategyName())
            .field("candidatePolicy", dryRunConfig.policy().name())
            .field("candidateRiskProfile", dryRunConfig.riskProfile().name())
            .field("fixedStake", dryRunConfig.fixedStake())
            .field("representativeFinalStake", stake.representativeFinalStake())
            .field("representativeStakeSource", stake.representativeStakeSource())
            .field("gateStatus", decision.status().name())
            .field("gatePassed", decision.gatePassed())
            .field("conceptuallyEligibleForLive", decision.conceptuallyEligibleForLive())
            .field("shouldApplyLive", decision.shouldApplyLive())
            .field("officiallyApplied", decision.officiallyApplied())
            .field("selectedStakeMode", decision.selectedStakeMode().name())
            .field("fallbackApplied", decision.fallbackApplied())
            .field("fallbackStake", decision.fallbackStake())
            .field("reasons", decision.reasons().stream().map(StakeSizingLiveGateReason::name).toList())
            .field("warnings", stake.warnings())
            .field("sampleSize", 0)
            .field("minSettledJoinedRequired", config.minSettledJoinedRequired())
            .field("shadowFailedCount", health.shadowFailedCount())
            .field("duplicateLogicalKeysCount", health.duplicateLogicalKeysCount())
            .field("forbiddenLiveEventsCount", health.forbiddenLiveEventsCount())
            .field("dryRun", true)
            .field("liveEnabled", config.liveEnabled())
            .emit();
    }

    private void logFailed(String cycleId, BetRecommendation recommendation, RuntimeException exc) {
        eventLogger.warn(BetxEventCategory.ERROR, "stake_sizing.live_gate_dry_run_failed")
            .correlationId("recommendation-" + recommendation.id())
            .cycleId(cycleId)
            .exchange(recommendation.exchange())
            .marketId(recommendation.marketId())
            .selectionId(recommendation.selectionId())
            .strategy(recommendation.strategyName())
            .executionMode("dry_run_live_gate")
            .result("failed")
            .field("recommendationId", recommendation.id())
            .field("canonicalKey", recommendation.canonicalKey())
            .field("errorType", exc.getClass().getSimpleName())
            .field("errorMessage", safeMessage(exc))
            .field("dryRun", true)
            .field("flowContinued", true)
            .emit();
    }

    private static String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        return message == null || message.isBlank() ? exc.getClass().getSimpleName() : message;
    }

    private record DryRunStake(
        BigDecimal representativeFinalStake,
        String representativeStakeSource,
        List<String> warnings
    ) {
        private DryRunStake {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
