package com.betx.domain.staking;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StakeSizingEngineTest {
    private static final Instant NOW = Instant.parse("2026-07-01T10:15:30Z");

    private final StakeSizingEngine engine = StakeSizingEngine.defaultEngine();

    @Test
    void flatUsesBaseStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context().baseStake("10.00").maxStake("100.00").build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("10.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.wouldBlock()).isFalse();
        assertThat(decision.decisionReason()).isEqualTo(StakeSizingDecisionReason.BASE_STAKE);
    }

    @Test
    void flatDoesNotExceedMaxStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context().baseStake("25.00").maxStake("10.00").build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("25.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("CAPPED_TO_MAX_STAKE");
    }

    @Test
    void flatRaisesPositiveStakeToMinStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context().baseStake("0.50").minStake("1.00").build());

        assertThat(decision.finalStake()).isEqualByComparingTo("1.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("RAISED_TO_MIN_STAKE");
    }

    @Test
    void flatBlocksWhenBudgetCannotCoverMinimumStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context()
            .baseStake("10.00")
            .minStake("1.00")
            .maxDailyLoss("25.00")
            .dailyLossSoFar("-24.50")
            .build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.finalStake()).isEqualByComparingTo("0.00");
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.BELOW_MIN_STAKE_AFTER_LIMITS);
    }

    @Test
    void tieredConfidenceAppliesLowMultiplier() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context().confidenceScore(20).build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("5.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("5.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("CONFIDENCE_LOW");
    }

    @Test
    void tieredConfidenceAppliesMediumMultiplier() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context().confidenceScore(55).build());

        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("CONFIDENCE_MEDIUM");
    }

    @Test
    void tieredConfidenceAppliesHighMultiplier() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context().confidenceScore(75).build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("15.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("15.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("CONFIDENCE_HIGH");
    }

    @Test
    void tieredConfidenceAppliesVeryHighMultiplier() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context().confidenceScore(90).build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("20.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("20.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("CONFIDENCE_VERY_HIGH");
    }

    @Test
    void tieredConfidenceFallsBackToBaseStakeWhenConfidenceIsMissing() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context().build());

        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.decisionReason()).isEqualTo(StakeSizingDecisionReason.CONFIDENCE_NOT_AVAILABLE);
    }

    @Test
    void tieredConfidenceNeverExceedsMaxStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.TIERED_CONFIDENCE, context()
            .baseStake("10.00")
            .maxStake("12.00")
            .confidenceScore(90)
            .build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("20.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("12.00");
    }

    @Test
    void riskAdjustedHomeOddsTwoPointFiveKeepsBaseStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context()
            .selectionSide(SelectionSide.HOME)
            .odds("2.50")
            .build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("10.00");
        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.adjustments()).isEmpty();
    }

    @Test
    void riskAdjustedDrawReducesStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().selectionSide(SelectionSide.DRAW).build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("5.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("DRAW_REDUCTION");
    }

    @Test
    void riskAdjustedAwayReducesStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().selectionSide(SelectionSide.AWAY).build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("7.50");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("AWAY_REDUCTION");
    }

    @Test
    void riskAdjustedOddsFourReducesStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().odds("4.00").build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("5.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("ODDS_4_PLUS_REDUCTION");
    }

    @Test
    void riskAdjustedOddsFiveReducesMoreThanOddsFour() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().odds("5.00").build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("2.50");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("ODDS_5_PLUS_REDUCTION");
    }

    @Test
    void riskAdjustedDrawAndOddsFiveAccumulateReductions() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context()
            .selectionSide(SelectionSide.DRAW)
            .odds("5.20")
            .build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("1.25");
        assertThat(decision.finalStake()).isEqualByComparingTo("1.25");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name)
            .contains("DRAW_REDUCTION", "ODDS_5_PLUS_REDUCTION");
    }

    @Test
    void riskAdjustedHighDrawdownReducesStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().currentDrawdown("20.00").build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("5.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("DRAWDOWN_REDUCTION");
    }

    @Test
    void riskAdjustedHighOpenExposureReducesStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context()
            .openExposure("45.00")
            .maxTotalExposure("50.00")
            .build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("5.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name).contains("OPEN_EXPOSURE_REDUCTION");
    }

    @Test
    void riskAdjustedRecordsAdjustmentMultiplierAndReason() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context().selectionSide(SelectionSide.DRAW).build());

        StakeSizingAdjustment adjustment = decision.adjustments().getFirst();
        assertThat(adjustment.name()).isEqualTo("DRAW_REDUCTION");
        assertThat(adjustment.multiplier()).isEqualByComparingTo("0.50");
        assertThat(adjustment.reason()).isEqualTo("Draw selections use conservative stake reduction.");
    }

    @Test
    void riskAdjustedNeverExceedsMaxStakeOrBudgets() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.RISK_ADJUSTED, context()
            .riskProfile(StakeSizingRiskProfile.AGGRESSIVE)
            .maxStake("11.00")
            .maxMarketExposure("12.00")
            .marketExposure("2.00")
            .build());

        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.finalStake()).isLessThanOrEqualTo(decision.maxStake());
    }

    @Test
    void fractionalKellyWithoutProbabilityIsNotAvailable() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FRACTIONAL_KELLY_SHADOW, context().build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.finalStake()).isEqualByComparingTo("0.00");
        assertThat(decision.decisionReason()).isEqualTo(StakeSizingDecisionReason.PROBABILITY_NOT_AVAILABLE);
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.NOT_AVAILABLE);
        assertThat(decision.shadowOnly()).isTrue();
    }

    @Test
    void fractionalKellyNegativeKellyBlocksWithClearReason() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FRACTIONAL_KELLY_SHADOW, context()
            .odds("3.00")
            .estimatedProbability("0.20")
            .build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.finalStake()).isEqualByComparingTo("0.00");
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.NEGATIVE_OR_ZERO_KELLY);
    }

    @Test
    void fractionalKellyPositiveKellyCalculatesStakeWithDefaultFraction() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FRACTIONAL_KELLY_SHADOW, context()
            .bankroll("500.00")
            .odds("3.00")
            .estimatedProbability("0.40")
            .maxStake("100.00")
            .build());

        assertThat(decision.calculatedStake()).isEqualByComparingTo("12.50");
        assertThat(decision.finalStake()).isEqualByComparingTo("10.00");
        assertThat(decision.adjustments()).extracting(StakeSizingAdjustment::name)
            .contains("FRACTIONAL_KELLY_0_25", "CAPPED_TO_MAX_KELLY_BANKROLL_PERCENT");
        assertThat(decision.shadowOnly()).isTrue();
    }

    @Test
    void fractionalKellyAppliesMaxStake() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FRACTIONAL_KELLY_SHADOW, context()
            .bankroll("500.00")
            .odds("3.00")
            .estimatedProbability("0.40")
            .maxStake("8.00")
            .build());

        assertThat(decision.finalStake()).isEqualByComparingTo("8.00");
    }

    @Test
    void safetyBlocksByDailyLossBudget() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context()
            .maxDailyLoss("25.00")
            .dailyLossSoFar("-25.00")
            .build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.BLOCKED_BY_RISK_BUDGET);
    }

    @Test
    void safetyBlocksByTotalExposureBudget() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context()
            .maxTotalExposure("50.00")
            .openExposure("50.00")
            .build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.BLOCKED_BY_RISK_BUDGET);
    }

    @Test
    void safetyBlocksByMarketExposureBudget() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context()
            .maxMarketExposure("5.00")
            .marketExposure("5.00")
            .build());

        assertThat(decision.wouldBlock()).isTrue();
        assertThat(decision.blockReason()).isEqualTo(StakeSizingBlockReason.BLOCKED_BY_RISK_BUDGET);
    }

    @Test
    void finalStakeIsNeverMaxStakeByDefaultWhenBaseStakeIsLower() {
        StakeSizingDecision decision = engine.evaluate(StakeSizingMode.FLAT, context().baseStake("1.00").maxStake("100.00").build());

        assertThat(decision.finalStake()).isEqualByComparingTo("1.00");
        assertThat(decision.finalStake()).isNotEqualByComparingTo(decision.maxStake());
    }

    private static StakeSizingContextBuilder context() {
        return new StakeSizingContextBuilder();
    }

    private static final class StakeSizingContextBuilder {
        private String recommendationId = "rec-1";
        private String canonicalKey = "betfair|1.234|42|HOME|value-football";
        private String strategyName = "value-football";
        private SelectionSide selectionSide = SelectionSide.HOME;
        private String odds = "2.50";
        private String baseStake = "10.00";
        private String minStake = "1.00";
        private String maxStake = "100.00";
        private String bankroll = "500.00";
        private StakeSizingRiskProfile riskProfile = StakeSizingRiskProfile.BALANCED;
        private StakeSizingSource source = StakeSizingSource.SHADOW;
        private String estimatedProbability;
        private Integer confidenceScore;
        private String currentDrawdown;
        private String openExposure = "0.00";
        private String dailyLossSoFar = "0.00";
        private String marketExposure = "0.00";
        private String maxDailyLoss = "25.00";
        private String maxTotalExposure = "50.00";
        private String maxMarketExposure = "50.00";

        private StakeSizingContextBuilder selectionSide(SelectionSide value) {
            selectionSide = value;
            return this;
        }

        private StakeSizingContextBuilder odds(String value) {
            odds = value;
            return this;
        }

        private StakeSizingContextBuilder baseStake(String value) {
            baseStake = value;
            return this;
        }

        private StakeSizingContextBuilder minStake(String value) {
            minStake = value;
            return this;
        }

        private StakeSizingContextBuilder maxStake(String value) {
            maxStake = value;
            return this;
        }

        private StakeSizingContextBuilder bankroll(String value) {
            bankroll = value;
            return this;
        }

        private StakeSizingContextBuilder riskProfile(StakeSizingRiskProfile value) {
            riskProfile = value;
            return this;
        }

        private StakeSizingContextBuilder estimatedProbability(String value) {
            estimatedProbability = value;
            return this;
        }

        private StakeSizingContextBuilder confidenceScore(Integer value) {
            confidenceScore = value;
            return this;
        }

        private StakeSizingContextBuilder currentDrawdown(String value) {
            currentDrawdown = value;
            return this;
        }

        private StakeSizingContextBuilder openExposure(String value) {
            openExposure = value;
            return this;
        }

        private StakeSizingContextBuilder dailyLossSoFar(String value) {
            dailyLossSoFar = value;
            return this;
        }

        private StakeSizingContextBuilder marketExposure(String value) {
            marketExposure = value;
            return this;
        }

        private StakeSizingContextBuilder maxDailyLoss(String value) {
            maxDailyLoss = value;
            return this;
        }

        private StakeSizingContextBuilder maxTotalExposure(String value) {
            maxTotalExposure = value;
            return this;
        }

        private StakeSizingContextBuilder maxMarketExposure(String value) {
            maxMarketExposure = value;
            return this;
        }

        private StakeSizingContext build() {
            return new StakeSizingContext(
                recommendationId,
                canonicalKey,
                strategyName,
                selectionSide,
                decimal(odds),
                decimal(baseStake),
                decimal(minStake),
                decimal(maxStake),
                decimal(bankroll),
                riskProfile,
                source,
                decimal(estimatedProbability),
                confidenceScore,
                decimal(currentDrawdown),
                decimal(openExposure),
                decimal(dailyLossSoFar),
                decimal(marketExposure),
                decimal(maxDailyLoss),
                decimal(maxTotalExposure),
                decimal(maxMarketExposure),
                NOW
            );
        }

        private static BigDecimal decimal(String value) {
            return value == null ? null : new BigDecimal(value);
        }
    }
}
