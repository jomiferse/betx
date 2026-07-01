package com.betx.domain.staking;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure domain engine for stake sizing decisions. It never executes or mutates live orders. */
public final class StakeSizingEngine {
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal LOW_CONFIDENCE_MULTIPLIER = decimal("0.50");
    private static final BigDecimal MEDIUM_CONFIDENCE_MULTIPLIER = decimal("1.00");
    private static final BigDecimal HIGH_CONFIDENCE_MULTIPLIER = decimal("1.50");
    private static final BigDecimal VERY_HIGH_CONFIDENCE_MULTIPLIER = decimal("2.00");
    private static final BigDecimal DRAW_MULTIPLIER = decimal("0.50");
    private static final BigDecimal AWAY_MULTIPLIER = decimal("0.75");
    private static final BigDecimal ODDS_4_PLUS_MULTIPLIER = decimal("0.50");
    private static final BigDecimal ODDS_5_PLUS_MULTIPLIER = decimal("0.25");
    private static final BigDecimal DRAWDOWN_MULTIPLIER = decimal("0.50");
    private static final BigDecimal OPEN_EXPOSURE_MULTIPLIER = decimal("0.50");
    private static final BigDecimal DRAWDOWN_REDUCTION_THRESHOLD = decimal("10.00");
    private static final BigDecimal HIGH_EXPOSURE_RATIO = decimal("0.80");
    private static final BigDecimal KELLY_FRACTION = decimal("0.25");
    private static final BigDecimal MAX_KELLY_STAKE_PCT_BANKROLL = decimal("0.02");

    private final Clock clock;
    private final Map<StakeSizingMode, StakeSizingPolicy> policies;

    private StakeSizingEngine(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.policies = new EnumMap<>(StakeSizingMode.class);
        register(new FlatPolicy());
        register(new TieredConfidencePolicy());
        register(new RiskAdjustedPolicy());
        register(new FractionalKellyShadowPolicy());
    }

    public static StakeSizingEngine defaultEngine() {
        return new StakeSizingEngine(Clock.systemUTC());
    }

    public StakeSizingDecision evaluate(StakeSizingMode mode, StakeSizingContext context) {
        StakeSizingMode effectiveMode = mode == null ? StakeSizingMode.FLAT : mode;
        StakeSizingPolicy policy = policies.get(effectiveMode);
        if (policy == null) {
            throw new IllegalArgumentException("Unsupported stake sizing mode: " + effectiveMode);
        }
        return policy.evaluate(context);
    }

    private void register(StakeSizingPolicy policy) {
        policies.put(policy.mode(), policy);
    }

    private StakeSizingDecision unavailable(
        StakeSizingMode mode,
        StakeSizingContext context,
        StakeSizingDecisionReason decisionReason,
        StakeSizingBlockReason blockReason,
        List<StakeSizingAdjustment> adjustments,
        boolean shadowOnly
    ) {
        return new StakeSizingDecision(
            context.recommendationId(),
            context.canonicalKey(),
            mode,
            context.riskProfile(),
            money(context.baseStake()),
            money(BigDecimal.ZERO),
            money(BigDecimal.ZERO),
            money(context.minStake()),
            money(context.maxStake()),
            context.odds(),
            context.selectionSide(),
            true,
            blockReason,
            decisionReason,
            adjustments,
            context.source(),
            context.createdAt(),
            Instant.now(clock),
            shadowOnly
        );
    }

    private StakeSizingDecision applySafetyLimits(
        StakeSizingMode mode,
        StakeSizingContext context,
        BigDecimal calculatedStake,
        StakeSizingDecisionReason decisionReason,
        List<StakeSizingAdjustment> adjustments,
        boolean shadowOnly
    ) {
        return applySafetyLimits(mode, context, calculatedStake, calculatedStake, decisionReason, adjustments, shadowOnly);
    }

    private StakeSizingDecision applySafetyLimits(
        StakeSizingMode mode,
        StakeSizingContext context,
        BigDecimal calculatedStake,
        BigDecimal stakeForLimits,
        StakeSizingDecisionReason decisionReason,
        List<StakeSizingAdjustment> adjustments,
        boolean shadowOnly
    ) {
        List<StakeSizingAdjustment> allAdjustments = new ArrayList<>(adjustments);
        BigDecimal adjustedCalculatedStake = applyRiskProfile(context.riskProfile(), calculatedStake, allAdjustments);
        BigDecimal adjustedStake = context.riskProfile() == StakeSizingRiskProfile.BALANCED
            ? stakeForLimits
            : stakeForLimits.multiply(context.riskProfile().multiplier());
        BigDecimal finalStake = adjustedStake.min(context.maxStake());
        if (finalStake.compareTo(adjustedStake) < 0) {
            allAdjustments.add(new StakeSizingAdjustment(
                "CAPPED_TO_MAX_STAKE",
                BigDecimal.ONE,
                "Stake capped by configured max stake."
            ));
        }

        BudgetLimit budgetLimit = tightestBudget(context);
        if (budgetLimit.remaining().compareTo(BigDecimal.ZERO) <= 0) {
            return blocked(mode, context, money(adjustedCalculatedStake), StakeSizingBlockReason.BLOCKED_BY_RISK_BUDGET, decisionReason, allAdjustments, shadowOnly);
        }
        if (budgetLimit.remaining().compareTo(context.minStake()) < 0) {
            return blocked(mode, context, money(adjustedCalculatedStake), StakeSizingBlockReason.BELOW_MIN_STAKE_AFTER_LIMITS, decisionReason, allAdjustments, shadowOnly);
        }
        if (finalStake.compareTo(budgetLimit.remaining()) > 0) {
            finalStake = budgetLimit.remaining();
            allAdjustments.add(new StakeSizingAdjustment(
                "CAPPED_TO_" + budgetLimit.name(),
                BigDecimal.ONE,
                "Stake capped by remaining " + budgetLimit.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + "."
            ));
        }
        if (finalStake.compareTo(BigDecimal.ZERO) > 0 && finalStake.compareTo(context.minStake()) < 0) {
            finalStake = context.minStake();
            allAdjustments.add(new StakeSizingAdjustment(
                "RAISED_TO_MIN_STAKE",
                BigDecimal.ONE,
                "Positive calculated stake raised to configured minimum stake."
            ));
        }
        return new StakeSizingDecision(
            context.recommendationId(),
            context.canonicalKey(),
            mode,
            context.riskProfile(),
            money(context.baseStake()),
            money(adjustedCalculatedStake),
            money(finalStake),
            money(context.minStake()),
            money(context.maxStake()),
            context.odds(),
            context.selectionSide(),
            false,
            null,
            decisionReason,
            allAdjustments,
            context.source(),
            context.createdAt(),
            Instant.now(clock),
            shadowOnly
        );
    }

    private StakeSizingDecision blocked(
        StakeSizingMode mode,
        StakeSizingContext context,
        BigDecimal calculatedStake,
        StakeSizingBlockReason blockReason,
        StakeSizingDecisionReason decisionReason,
        List<StakeSizingAdjustment> adjustments,
        boolean shadowOnly
    ) {
        return new StakeSizingDecision(
            context.recommendationId(),
            context.canonicalKey(),
            mode,
            context.riskProfile(),
            money(context.baseStake()),
            money(calculatedStake),
            money(BigDecimal.ZERO),
            money(context.minStake()),
            money(context.maxStake()),
            context.odds(),
            context.selectionSide(),
            true,
            blockReason,
            decisionReason,
            adjustments,
            context.source(),
            context.createdAt(),
            Instant.now(clock),
            shadowOnly
        );
    }

    private BigDecimal applyRiskProfile(
        StakeSizingRiskProfile riskProfile,
        BigDecimal stake,
        List<StakeSizingAdjustment> adjustments
    ) {
        if (riskProfile == StakeSizingRiskProfile.BALANCED) {
            return stake;
        }
        adjustments.add(new StakeSizingAdjustment(
            "RISK_PROFILE_" + riskProfile.name(),
            riskProfile.multiplier(),
            "Risk profile multiplier applied."
        ));
        return stake.multiply(riskProfile.multiplier());
    }

    private BudgetLimit tightestBudget(StakeSizingContext context) {
        BudgetLimit daily = new BudgetLimit("DAILY_LOSS_BUDGET", context.maxDailyLoss().subtract(context.dailyLossSoFar().abs()));
        BudgetLimit total = new BudgetLimit("TOTAL_EXPOSURE_BUDGET", context.maxTotalExposure().subtract(context.openExposure()));
        BudgetLimit market = new BudgetLimit("MARKET_EXPOSURE_BUDGET", context.maxMarketExposure().subtract(context.marketExposure()));
        BudgetLimit tightest = daily.remaining().compareTo(total.remaining()) <= 0 ? daily : total;
        return tightest.remaining().compareTo(market.remaining()) <= 0 ? tightest : market;
    }

    private BigDecimal multiply(BigDecimal stake, BigDecimal multiplier, List<StakeSizingAdjustment> adjustments, String name, String reason) {
        adjustments.add(new StakeSizingAdjustment(name, multiplier, reason));
        return stake.multiply(multiplier);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record BudgetLimit(String name, BigDecimal remaining) {
    }

    private final class FlatPolicy implements StakeSizingPolicy {
        @Override
        public StakeSizingMode mode() {
            return StakeSizingMode.FLAT;
        }

        @Override
        public StakeSizingDecision evaluate(StakeSizingContext context) {
            return applySafetyLimits(mode(), context, context.baseStake(), StakeSizingDecisionReason.BASE_STAKE, List.of(), false);
        }
    }

    private final class TieredConfidencePolicy implements StakeSizingPolicy {
        @Override
        public StakeSizingMode mode() {
            return StakeSizingMode.TIERED_CONFIDENCE;
        }

        @Override
        public StakeSizingDecision evaluate(StakeSizingContext context) {
            if (context.confidenceScore() == null) {
                return applySafetyLimits(
                    mode(),
                    context,
                    context.baseStake(),
                    StakeSizingDecisionReason.CONFIDENCE_NOT_AVAILABLE,
                    List.of(),
                    false
                );
            }
            ConfidenceTier tier = ConfidenceTier.from(context.confidenceScore());
            BigDecimal stake = context.baseStake().multiply(tier.multiplier());
            return applySafetyLimits(
                mode(),
                context,
                stake,
                StakeSizingDecisionReason.CONFIDENCE_SCORE,
                List.of(new StakeSizingAdjustment(tier.adjustmentName(), tier.multiplier(), tier.reason())),
                false
            );
        }
    }

    private final class RiskAdjustedPolicy implements StakeSizingPolicy {
        @Override
        public StakeSizingMode mode() {
            return StakeSizingMode.RISK_ADJUSTED;
        }

        @Override
        public StakeSizingDecision evaluate(StakeSizingContext context) {
            List<StakeSizingAdjustment> adjustments = new ArrayList<>();
            BigDecimal stake = context.baseStake();
            if (context.selectionSide() == SelectionSide.DRAW) {
                stake = multiply(stake, DRAW_MULTIPLIER, adjustments, "DRAW_REDUCTION", "Draw selections use conservative stake reduction.");
            } else if (context.selectionSide() == SelectionSide.AWAY) {
                stake = multiply(stake, AWAY_MULTIPLIER, adjustments, "AWAY_REDUCTION", "Away selections use conservative stake reduction.");
            }
            if (context.odds().compareTo(decimal("5.00")) >= 0) {
                stake = multiply(stake, ODDS_5_PLUS_MULTIPLIER, adjustments, "ODDS_5_PLUS_REDUCTION", "Odds at or above 5.00 use strong stake reduction.");
            } else if (context.odds().compareTo(decimal("4.00")) >= 0) {
                stake = multiply(stake, ODDS_4_PLUS_MULTIPLIER, adjustments, "ODDS_4_PLUS_REDUCTION", "Odds at or above 4.00 use stake reduction.");
            }
            if (context.currentDrawdown() != null && context.currentDrawdown().abs().compareTo(DRAWDOWN_REDUCTION_THRESHOLD) >= 0) {
                stake = multiply(stake, DRAWDOWN_MULTIPLIER, adjustments, "DRAWDOWN_REDUCTION", "High current drawdown uses conservative stake reduction.");
            }
            if (context.maxTotalExposure().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal exposureRatio = context.openExposure().divide(context.maxTotalExposure(), 4, RoundingMode.HALF_UP);
                if (exposureRatio.compareTo(HIGH_EXPOSURE_RATIO) >= 0) {
                    stake = multiply(stake, OPEN_EXPOSURE_MULTIPLIER, adjustments, "OPEN_EXPOSURE_REDUCTION", "High open exposure uses conservative stake reduction.");
                }
            }
            return applySafetyLimits(mode(), context, stake, StakeSizingDecisionReason.RISK_ADJUSTED, adjustments, false);
        }
    }

    private final class FractionalKellyShadowPolicy implements StakeSizingPolicy {
        @Override
        public StakeSizingMode mode() {
            return StakeSizingMode.FRACTIONAL_KELLY_SHADOW;
        }

        @Override
        public StakeSizingDecision evaluate(StakeSizingContext context) {
            if (context.estimatedProbability() == null) {
                return unavailable(
                    mode(),
                    context,
                    StakeSizingDecisionReason.PROBABILITY_NOT_AVAILABLE,
                    StakeSizingBlockReason.NOT_AVAILABLE,
                    List.of(),
                    true
                );
            }
            BigDecimal oddsMinusOne = context.odds().subtract(BigDecimal.ONE);
            BigDecimal kellyFraction = context.estimatedProbability().multiply(context.odds())
                .subtract(BigDecimal.ONE)
                .divide(oddsMinusOne, 8, RoundingMode.HALF_UP);
            if (kellyFraction.compareTo(BigDecimal.ZERO) <= 0) {
                return unavailable(
                    mode(),
                    context,
                    StakeSizingDecisionReason.FRACTIONAL_KELLY,
                    StakeSizingBlockReason.NEGATIVE_OR_ZERO_KELLY,
                    List.of(),
                    true
                );
            }
            List<StakeSizingAdjustment> adjustments = new ArrayList<>();
            adjustments.add(new StakeSizingAdjustment(
                "FRACTIONAL_KELLY_0_25",
                KELLY_FRACTION,
                "Kelly stake multiplied by conservative 0.25 fraction."
            ));
            BigDecimal rawStake = context.bankroll().multiply(kellyFraction).multiply(KELLY_FRACTION);
            BigDecimal stake = rawStake;
            BigDecimal maxKellyStake = context.bankroll().multiply(MAX_KELLY_STAKE_PCT_BANKROLL);
            if (stake.compareTo(maxKellyStake) > 0) {
                stake = maxKellyStake;
                adjustments.add(new StakeSizingAdjustment(
                    "CAPPED_TO_MAX_KELLY_BANKROLL_PERCENT",
                    BigDecimal.ONE,
                    "Kelly stake capped to maximum bankroll percentage."
                ));
            }
            return applySafetyLimits(mode(), context, rawStake, stake, StakeSizingDecisionReason.FRACTIONAL_KELLY, adjustments, true);
        }
    }

    private enum ConfidenceTier {
        LOW(LOW_CONFIDENCE_MULTIPLIER, "CONFIDENCE_LOW", "Low confidence uses half base stake."),
        MEDIUM(MEDIUM_CONFIDENCE_MULTIPLIER, "CONFIDENCE_MEDIUM", "Medium confidence uses base stake."),
        HIGH(HIGH_CONFIDENCE_MULTIPLIER, "CONFIDENCE_HIGH", "High confidence increases base stake."),
        VERY_HIGH(VERY_HIGH_CONFIDENCE_MULTIPLIER, "CONFIDENCE_VERY_HIGH", "Very high confidence increases base stake.");

        private final BigDecimal multiplier;
        private final String adjustmentName;
        private final String reason;

        ConfidenceTier(BigDecimal multiplier, String adjustmentName, String reason) {
            this.multiplier = multiplier;
            this.adjustmentName = adjustmentName;
            this.reason = reason;
        }

        private static ConfidenceTier from(int score) {
            if (score >= 85) {
                return VERY_HIGH;
            }
            if (score >= 70) {
                return HIGH;
            }
            if (score >= 40) {
                return MEDIUM;
            }
            return LOW;
        }

        private BigDecimal multiplier() {
            return multiplier;
        }

        private String adjustmentName() {
            return adjustmentName;
        }

        private String reason() {
            return reason;
        }
    }
}
