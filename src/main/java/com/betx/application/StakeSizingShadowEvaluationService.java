package com.betx.application;

import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.application.port.out.StakeSizingShadowDecisionRepository;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.StakingConfig;
import com.betx.domain.staking.StakeSizingAdjustment;
import com.betx.domain.staking.StakeSizingContext;
import com.betx.domain.staking.StakeSizingDecision;
import com.betx.domain.staking.StakeSizingEngine;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Evaluates stake sizing policies as shadow analytics only. It never changes live stake or execution. */
@Service
public class StakeSizingShadowEvaluationService {
    private final StakeSizingShadowDecisionRepository repository;
    private final BetxEventLogger eventLogger;
    private final Clock clock;
    private final StakeSizingEngine engine;

    @Autowired
    public StakeSizingShadowEvaluationService(
        StakeSizingShadowDecisionRepository repository,
        BetxEventLogger eventLogger
    ) {
        this(repository, eventLogger, Clock.systemUTC());
    }

    public StakeSizingShadowEvaluationService(
        StakeSizingShadowDecisionRepository repository,
        BetxEventLogger eventLogger,
        Clock clock
    ) {
        this.repository = repository == null ? new NoopStakeSizingShadowDecisionRepository() : repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), this.clock) : eventLogger;
        this.engine = StakeSizingEngine.defaultEngine();
    }

    public void evaluate(BetxConfig config, String cycleId, BetRecommendation recommendation) {
        if (recommendation == null) {
            return;
        }
        BetxConfig effectiveConfig = config == null ? BetxConfig.defaults() : config;
        StakingConfig staking = effectiveConfig.staking();
        if (!staking.shadowEnabled() || !staking.shadow().enabled()) {
            return;
        }
        try {
            for (StakeSizingMode policy : staking.shadow().policies()) {
                for (StakeSizingRiskProfile riskProfile : staking.shadow().riskProfiles()) {
                    StakeSizingContext context = context(effectiveConfig, recommendation, riskProfile);
                    StakeSizingDecision decision = engine.evaluate(policy, context);
                    StakeSizingShadowDecision shadowDecision = toShadowDecision(decision, recommendation, context);
                    StakeSizingShadowDecisionUpsertResult result = repository.upsert(
                        effectiveConfig.storage().path(),
                        shadowDecision
                    );
                    if (shouldLog(result.action())) {
                        logDecision(cycleId, recommendation, result);
                    }
                }
            }
        } catch (RuntimeException exc) {
            eventLogger.warn(BetxEventCategory.ERROR, "stake_sizing.shadow_failed")
                .cycleId(cycleId)
                .exchange(recommendation.exchange())
                .marketId(recommendation.marketId())
                .selectionId(recommendation.selectionId())
                .strategy(recommendation.strategyName())
                .executionMode("shadow")
                .result("failed")
                .field("recommendationId", recommendation.id())
                .field("errorType", exc.getClass().getSimpleName())
                .field("message", safeMessage(exc))
                .emit();
        }
    }

    private StakeSizingContext context(
        BetxConfig config,
        BetRecommendation recommendation,
        StakeSizingRiskProfile riskProfile
    ) {
        StakingConfig staking = config.staking();
        BigDecimal odds = firstNonNull(
            recommendation.latestRecommendedOdds(),
            recommendation.recommendedOdds(),
            recommendation.bestRecommendedOdds()
        );
        return new StakeSizingContext(
            recommendation.id(),
            recommendation.canonicalKey(),
            recommendation.strategyName(),
            recommendation.selectionSide(),
            odds,
            staking.baseStake(),
            staking.minStake(),
            staking.maxStake(),
            staking.bankroll(),
            riskProfile,
            StakeSizingSource.SHADOW,
            null,
            recommendation.confidence(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            staking.limits().maxDailyLoss(),
            staking.limits().maxTotalExposure(),
            staking.limits().maxMarketExposure(),
            evaluatedAt(recommendation)
        );
    }

    private StakeSizingShadowDecision toShadowDecision(
        StakeSizingDecision decision,
        BetRecommendation recommendation,
        StakeSizingContext context
    ) {
        return new StakeSizingShadowDecision(
            UUID.randomUUID().toString(),
            decision.recommendationId(),
            decision.canonicalKey(),
            decision.mode(),
            decision.riskProfile(),
            decision.source(),
            decision.selectionSide(),
            decision.odds(),
            recommendation.strategyName(),
            decision.baseStake(),
            decision.minStake(),
            decision.maxStake(),
            context.bankroll(),
            decision.calculatedStake(),
            decision.finalStake(),
            decision.wouldBlock(),
            decision.blockReason(),
            decision.decisionReason(),
            adjustmentSummary(decision.adjustments()),
            decision.evaluatedAt(),
            Instant.now(clock),
            decision.evaluatedAt(),
            1
        );
    }

    private void logDecision(
        String cycleId,
        BetRecommendation recommendation,
        StakeSizingShadowDecisionUpsertResult result
    ) {
        StakeSizingShadowDecision decision = result.decision();
        eventLogger.info(BetxEventCategory.ANALYTICS, "stake_sizing.shadow_evaluated")
            .correlationId("recommendation-" + recommendation.id())
            .cycleId(cycleId)
            .exchange(recommendation.exchange())
            .marketId(recommendation.marketId())
            .selectionId(recommendation.selectionId())
            .strategy(recommendation.strategyName())
            .executionMode("shadow")
            .result(result.action().name().toLowerCase(java.util.Locale.ROOT))
            .field("recommendationId", decision.recommendationId())
            .field("canonicalKey", decision.canonicalKey())
            .field("policyName", decision.policyName().name())
            .field("riskProfile", decision.riskProfile().name())
            .field("source", decision.source().name())
            .field("selectionSide", decision.selectionSide().name())
            .field("odds", decision.odds())
            .field("strategyName", decision.strategyName())
            .field("baseStake", decision.baseStake())
            .field("minStake", decision.minStake())
            .field("maxStake", decision.maxStake())
            .field("bankroll", decision.bankroll())
            .field("calculatedStake", decision.calculatedStake())
            .field("finalStake", decision.finalStake())
            .field("wouldBlock", decision.wouldBlock())
            .field("blockReason", decision.blockReason() == null ? null : decision.blockReason().name())
            .field("decisionReason", decision.decisionReason().name())
            .field("adjustments", decision.adjustmentSummary())
            .field("upsertAction", result.action().name())
            .emit();
    }

    private static boolean shouldLog(StakeSizingShadowDecisionUpsertAction action) {
        return action == StakeSizingShadowDecisionUpsertAction.CREATED
            || action == StakeSizingShadowDecisionUpsertAction.UPDATED_DECISION_CHANGED
            || action == StakeSizingShadowDecisionUpsertAction.UPDATED_STAKE_CHANGED
            || action == StakeSizingShadowDecisionUpsertAction.UPDATED_REASON_CHANGED;
    }

    private Instant evaluatedAt(BetRecommendation recommendation) {
        if (recommendation.lastSeenAt() != null) {
            return recommendation.lastSeenAt();
        }
        if (recommendation.recommendedAt() != null) {
            return recommendation.recommendedAt();
        }
        return Instant.now(clock);
    }

    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second, BigDecimal third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private static String adjustmentSummary(List<StakeSizingAdjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) {
            return "[]";
        }
        return adjustments.stream()
            .map(adjustment -> adjustment.name() + ":" + adjustment.multiplier().toPlainString())
            .toList()
            .toString();
    }

    private static String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        return message == null || message.isBlank() ? exc.getClass().getSimpleName() : message;
    }

    private static final class NoopStakeSizingShadowDecisionRepository implements StakeSizingShadowDecisionRepository {
        @Override
        public StakeSizingShadowDecisionUpsertResult upsert(String databasePath, StakeSizingShadowDecision decision) {
            return new StakeSizingShadowDecisionUpsertResult(
                decision,
                StakeSizingShadowDecisionUpsertAction.OBSERVED_UNCHANGED
            );
        }

        @Override
        public List<StakeSizingShadowDecision> list(String databasePath, Instant from, Instant to) {
            return List.of();
        }

        @Override
        public long countDuplicateLogicalKeys(String databasePath) {
            return 0;
        }
    }
}
