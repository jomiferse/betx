package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventLogger;
import com.betx.application.port.out.StakeSizingShadowDecisionRepository;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.staking.StakeSizingDecisionReason;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StakeSizingShadowEvaluationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void evaluatesAndPersistsFlatDecisionForRecommendation() {
        RecordingRepository repository = new RecordingRepository(StakeSizingShadowDecisionUpsertAction.CREATED);
        RecordingSink sink = new RecordingSink();
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(repository, new BetxEventLogger(sink, CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.HOME, "2.50", null));

        assertThat(repository.saved()).anySatisfy(decision -> {
            assertThat(decision.policyName()).isEqualTo(StakeSizingMode.FLAT);
            assertThat(decision.riskProfile()).isEqualTo(StakeSizingRiskProfile.BALANCED);
            assertThat(decision.finalStake()).isEqualByComparingTo("1.00");
        });
    }

    @Test
    void evaluatesRiskAdjustedForDrawWithHighOdds() {
        RecordingRepository repository = new RecordingRepository(StakeSizingShadowDecisionUpsertAction.CREATED);
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(repository, new BetxEventLogger(StructuredEventSink.noop(), CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.DRAW, "5.20", null));

        assertThat(repository.saved()).anySatisfy(decision -> {
            assertThat(decision.policyName()).isEqualTo(StakeSizingMode.RISK_ADJUSTED);
            assertThat(decision.riskProfile()).isEqualTo(StakeSizingRiskProfile.BALANCED);
            assertThat(decision.calculatedStake()).isEqualByComparingTo("0.13");
            assertThat(decision.finalStake()).isEqualByComparingTo("1.00");
            assertThat(decision.adjustmentSummary()).contains("DRAW_REDUCTION", "ODDS_5_PLUS_REDUCTION", "RAISED_TO_MIN_STAKE");
        });
    }

    @Test
    void tieredConfidenceUsesMissingConfidenceReasonWithoutInventingScore() {
        RecordingRepository repository = new RecordingRepository(StakeSizingShadowDecisionUpsertAction.CREATED);
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(repository, new BetxEventLogger(StructuredEventSink.noop(), CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.HOME, "2.50", null));

        assertThat(repository.saved()).anySatisfy(decision -> {
            assertThat(decision.policyName()).isEqualTo(StakeSizingMode.TIERED_CONFIDENCE);
            assertThat(decision.decisionReason()).isEqualTo(StakeSizingDecisionReason.CONFIDENCE_NOT_AVAILABLE);
        });
    }

    @Test
    void fractionalKellyMissingProbabilityPersistsShadowOnlyNotAvailableDecision() {
        RecordingRepository repository = new RecordingRepository(StakeSizingShadowDecisionUpsertAction.CREATED);
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(repository, new BetxEventLogger(StructuredEventSink.noop(), CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.HOME, "2.50", null));

        assertThat(repository.saved()).anySatisfy(decision -> {
            assertThat(decision.policyName()).isEqualTo(StakeSizingMode.FRACTIONAL_KELLY_SHADOW);
            assertThat(decision.riskProfile()).isEqualTo(StakeSizingRiskProfile.CONSERVATIVE);
            assertThat(decision.decisionReason()).isEqualTo(StakeSizingDecisionReason.PROBABILITY_NOT_AVAILABLE);
            assertThat(decision.wouldBlock()).isTrue();
            assertThat(decision.finalStake()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void emitsShadowEvaluatedLogOnlyForMeaningfulUpsertActions() {
        RecordingRepository repository = new RecordingRepository(
            StakeSizingShadowDecisionUpsertAction.CREATED,
            StakeSizingShadowDecisionUpsertAction.UPDATED_STAKE_CHANGED,
            StakeSizingShadowDecisionUpsertAction.UPDATED_REASON_CHANGED,
            StakeSizingShadowDecisionUpsertAction.OBSERVED_UNCHANGED,
            StakeSizingShadowDecisionUpsertAction.OBSERVED_UNCHANGED
        );
        RecordingSink sink = new RecordingSink();
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(repository, new BetxEventLogger(sink, CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.HOME, "2.50", null));

        assertThat(sink.events()).hasSize(3);
        assertThat(sink.events()).allSatisfy(event -> {
            assertThat(event.event()).isEqualTo("stake_sizing.shadow_evaluated");
            assertThat(event.fields()).containsKeys("recommendationId", "policyName", "riskProfile", "finalStake", "wouldBlock");
        });
        assertThat(sink.events()).noneSatisfy(event -> assertThat(event.event()).contains("live_applied"));
    }

    @Test
    void repositoryFailureDoesNotBreakMainFlow() {
        StakeSizingShadowDecisionRepository failingRepository = new StakeSizingShadowDecisionRepository() {
            @Override
            public StakeSizingShadowDecisionUpsertResult upsert(String databasePath, StakeSizingShadowDecision decision) {
                throw new IllegalStateException("sqlite unavailable");
            }

            @Override
            public List<StakeSizingShadowDecision> list(String databasePath, Instant from, Instant to) {
                return List.of();
            }

            @Override
            public long countDuplicateLogicalKeys(String databasePath) {
                return 0;
            }
        };
        RecordingSink sink = new RecordingSink();
        StakeSizingShadowEvaluationService service = new StakeSizingShadowEvaluationService(failingRepository, new BetxEventLogger(sink, CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(SelectionSide.HOME, "2.50", null));

        assertThat(sink.events()).anySatisfy(event -> {
            assertThat(event.event()).isEqualTo("stake_sizing.shadow_failed");
            assertThat(event.fields()).containsEntry("errorType", "IllegalStateException");
        });
    }

    private static BetRecommendation recommendation(SelectionSide side, String odds, Integer confidence) {
        return new BetRecommendation(
            "rec-1",
            "eval-1",
            "betfair",
            "1.234",
            42L,
            side,
            "Team A v Team B",
            "Team A",
            "La Liga",
            Instant.parse("2026-07-02T18:00:00Z"),
            "value-football",
            new BigDecimal(odds),
            Instant.parse("2026-07-01T10:00:00Z"),
            Instant.parse("2026-07-01T10:00:00Z"),
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            Instant.parse("2026-07-01T10:00:00Z"),
            confidence,
            null,
            new BigDecimal("1000.00"),
            "test"
        );
    }

    private static final class RecordingRepository implements StakeSizingShadowDecisionRepository {
        private final List<StakeSizingShadowDecision> saved = new ArrayList<>();
        private final StakeSizingShadowDecisionUpsertAction[] actions;
        private int index;

        private RecordingRepository(StakeSizingShadowDecisionUpsertAction... actions) {
            this.actions = actions;
        }

        @Override
        public StakeSizingShadowDecisionUpsertResult upsert(String databasePath, StakeSizingShadowDecision decision) {
            saved.add(decision);
            StakeSizingShadowDecisionUpsertAction action = actions[Math.min(index, actions.length - 1)];
            index++;
            return new StakeSizingShadowDecisionUpsertResult(decision, action);
        }

        @Override
        public List<StakeSizingShadowDecision> list(String databasePath, Instant from, Instant to) {
            return saved;
        }

        @Override
        public long countDuplicateLogicalKeys(String databasePath) {
            return 0;
        }

        private List<StakeSizingShadowDecision> saved() {
            return saved;
        }
    }

    private static final class RecordingSink implements StructuredEventSink {
        private final List<BetxEvent> events = new ArrayList<>();

        @Override
        public void emit(BetxEvent event) {
            events.add(event);
        }

        private List<BetxEvent> events() {
            return events;
        }
    }
}
