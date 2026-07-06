package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventLogger;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.staking.StakeSizingDecisionReason;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import com.betx.domain.staking.livegate.StakeSizingLiveGateContext;
import com.betx.domain.staking.livegate.StakeSizingLiveGateDecision;
import com.betx.domain.staking.livegate.StakeSizingLiveGateEvaluator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StakeSizingLiveGateDryRunServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void emitsDryRunEvaluationWithoutAllowingLiveStakeApplication() {
        RecordingSink sink = new RecordingSink();
        StakeSizingLiveGateDryRunService service = new StakeSizingLiveGateDryRunService(new BetxEventLogger(sink, CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(), riskAdjustedDecision("3.75"));

        assertThat(sink.events()).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("stake_sizing.live_gate_dry_run_evaluated");
            assertThat(event.fields()).containsEntry("recommendationId", "rec-1");
            assertThat(event.fields()).containsEntry("canonicalKey", "betfair|1.1|42|HOME|value-football");
            assertThat(event.fields()).containsEntry("candidatePolicy", "RISK_ADJUSTED");
            assertThat(event.fields()).containsEntry("candidateRiskProfile", "CONSERVATIVE");
            assertThat((BigDecimal) event.fields().get("fixedStake")).isEqualByComparingTo("1.00");
            assertThat((BigDecimal) event.fields().get("representativeFinalStake")).isEqualByComparingTo("3.75");
            assertThat(event.fields()).containsEntry("representativeStakeSource", "CURRENT_SHADOW_DECISION");
            assertThat(event.fields()).containsEntry("gateStatus", "FAIL");
            assertThat(event.fields()).containsEntry("shouldApplyLive", false);
            assertThat(event.fields()).containsEntry("officiallyApplied", false);
            assertThat((BigDecimal) event.fields().get("fallbackStake")).isEqualByComparingTo("1.00");
            assertThat(event.fields()).containsEntry("dryRun", true);
            assertThat(event.fields()).containsEntry("liveEnabled", false);
            assertThat(event.fields()).containsEntry("sampleSize", 0);
            assertThat(event.fields()).containsEntry("minSettledJoinedRequired", 100);
            assertThat(event.fields()).containsKey("reasons");
        });
        assertThat(sink.events()).noneSatisfy(event -> assertThat(event.event()).contains("live_applied"));
        assertThat(sink.events()).noneSatisfy(event -> assertThat(event.event()).contains("order_stake_changed"));
    }

    @Test
    void usesFixedStakeFallbackAndWarningWhenCurrentShadowDecisionIsMissing() {
        RecordingSink sink = new RecordingSink();
        StakeSizingLiveGateDryRunService service = new StakeSizingLiveGateDryRunService(new BetxEventLogger(sink, CLOCK), CLOCK);

        service.evaluate(BetxConfig.defaults(), "cycle-1", recommendation(), null);

        assertThat(sink.events()).singleElement().satisfies(event -> {
            assertThat((BigDecimal) event.fields().get("representativeFinalStake")).isEqualByComparingTo("1.00");
            assertThat(event.fields()).containsEntry("representativeStakeSource", "FALLBACK_NO_CURRENT_SHADOW_DECISION");
            assertThat(event.fields().get("warnings").toString()).contains("LIVE_GATE_DRY_RUN_STAKE_DECISION_MISSING");
            assertThat(event.fields()).containsEntry("shouldApplyLive", false);
            assertThat(event.fields()).containsEntry("officiallyApplied", false);
        });
    }

    @Test
    void dryRunFailureIsLoggedAndDoesNotThrow() {
        RecordingSink sink = new RecordingSink();
        StakeSizingLiveGateDryRunService service = new StakeSizingLiveGateDryRunService(
            new BetxEventLogger(sink, CLOCK),
            CLOCK,
            new ThrowingLiveGateEvaluator()
        );

        service.evaluate(BetxConfig.defaults(), "cycle-1", brokenRecommendation(), riskAdjustedDecision("3.75"));

        assertThat(sink.events()).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("stake_sizing.live_gate_dry_run_failed");
            assertThat(event.fields()).containsEntry("recommendationId", "rec-broken");
            assertThat(event.fields()).containsEntry("errorType", "IllegalArgumentException");
            assertThat(event.fields()).containsEntry("dryRun", true);
            assertThat(event.fields()).containsEntry("flowContinued", true);
        });
    }

    private static BetRecommendation recommendation() {
        return new BetRecommendation(
            "rec-1",
            "eval-1",
            "betfair",
            "1.1",
            42L,
            SelectionSide.HOME,
            "Team A v Team B",
            "Team A",
            "La Liga",
            Instant.parse("2026-07-07T18:00:00Z"),
            "value-football",
            new BigDecimal("2.50"),
            Instant.parse("2026-07-06T09:55:00Z"),
            Instant.parse("2026-07-06T09:55:00Z"),
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            Instant.parse("2026-07-06T09:55:00Z"),
            null,
            null,
            new BigDecimal("1000.00"),
            "test"
        );
    }

    private static BetRecommendation brokenRecommendation() {
        return new BetRecommendation(
            "rec-broken",
            "eval-1",
            "betfair",
            "1.1",
            42L,
            SelectionSide.HOME,
            "Team A v Team B",
            "Team A",
            "La Liga",
            Instant.parse("2026-07-07T18:00:00Z"),
            "value-football",
            new BigDecimal("-2.50"),
            Instant.parse("2026-07-06T09:55:00Z"),
            Instant.parse("2026-07-06T09:55:00Z"),
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            Instant.parse("2026-07-06T09:55:00Z"),
            null,
            null,
            new BigDecimal("1000.00"),
            "test"
        );
    }

    private static StakeSizingShadowDecision riskAdjustedDecision(String finalStake) {
        return new StakeSizingShadowDecision(
            "stake-1",
            "rec-1",
            "betfair|1.1|42|HOME|value-football",
            StakeSizingMode.RISK_ADJUSTED,
            StakeSizingRiskProfile.CONSERVATIVE,
            StakeSizingSource.SHADOW,
            SelectionSide.HOME,
            new BigDecimal("2.50"),
            "value-football",
            new BigDecimal("5.00"),
            new BigDecimal("1.00"),
            new BigDecimal("5.00"),
            new BigDecimal("500.00"),
            new BigDecimal(finalStake),
            new BigDecimal(finalStake),
            false,
            null,
            StakeSizingDecisionReason.RISK_ADJUSTED,
            "[]",
            Instant.parse("2026-07-06T10:00:00Z"),
            Instant.parse("2026-07-06T10:00:00Z"),
            Instant.parse("2026-07-06T10:00:00Z"),
            1
        );
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

    private static final class ThrowingLiveGateEvaluator extends StakeSizingLiveGateEvaluator {
        @Override
        public StakeSizingLiveGateDecision evaluate(StakeSizingLiveGateContext context) {
            throw new IllegalArgumentException("mapper failed");
        }
    }
}
