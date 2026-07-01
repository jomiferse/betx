package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.StakeSizingShadowDecision;
import com.betx.application.StakeSizingShadowDecisionUpsertAction;
import com.betx.application.StakeSizingShadowDecisionUpsertResult;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.staking.StakeSizingBlockReason;
import com.betx.domain.staking.StakeSizingDecisionReason;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcStakeSizingShadowDecisionRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void firstDecisionCreatesRow() {
        String databasePath = tempDir.resolve("stake-sizing.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);

        StakeSizingShadowDecisionUpsertResult result = repository.upsert(databasePath, decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.BALANCED));

        assertThat(result.action()).isEqualTo(StakeSizingShadowDecisionUpsertAction.CREATED);
        assertThat(result.decision().observedCount()).isEqualTo(1);
        assertThat(repository.list(databasePath, null, null)).singleElement().satisfies(saved -> {
            assertThat(saved.recommendationId()).isEqualTo("rec-1");
            assertThat(saved.policyName()).isEqualTo(StakeSizingMode.FLAT);
            assertThat(saved.riskProfile()).isEqualTo(StakeSizingRiskProfile.BALANCED);
            assertThat(saved.finalStake()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void repeatedSameLogicalKeyDoesNotDuplicateAndIncrementsObservedCount() {
        String databasePath = tempDir.resolve("stake-sizing-dedupe.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);
        StakeSizingShadowDecision first = decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.BALANCED);
        StakeSizingShadowDecision second = first.withLatest(
            first.calculatedStake(),
            first.finalStake(),
            first.wouldBlock(),
            first.blockReason(),
            first.decisionReason(),
            first.adjustmentSummary(),
            new BigDecimal("2.80"),
            Instant.parse("2026-07-01T11:00:00Z")
        );

        repository.upsert(databasePath, first);
        StakeSizingShadowDecisionUpsertResult result = repository.upsert(databasePath, second);

        assertThat(result.action()).isEqualTo(StakeSizingShadowDecisionUpsertAction.OBSERVED_UNCHANGED);
        assertThat(result.decision().observedCount()).isEqualTo(2);
        assertThat(result.decision().odds()).isEqualByComparingTo("2.80");
        assertThat(repository.list(databasePath, null, null)).hasSize(1);
        assertThat(repository.countDuplicateLogicalKeys(databasePath)).isZero();
    }

    @Test
    void finalStakeChangeUpdatesRowAndReturnsStakeChanged() {
        String databasePath = tempDir.resolve("stake-sizing-stake-change.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);
        StakeSizingShadowDecision first = decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.BALANCED);
        StakeSizingShadowDecision second = first.withLatest(
            new BigDecimal("12.00"),
            new BigDecimal("12.00"),
            false,
            null,
            first.decisionReason(),
            first.adjustmentSummary(),
            first.odds(),
            Instant.parse("2026-07-01T11:00:00Z")
        );

        repository.upsert(databasePath, first);
        StakeSizingShadowDecisionUpsertResult result = repository.upsert(databasePath, second);

        assertThat(result.action()).isEqualTo(StakeSizingShadowDecisionUpsertAction.UPDATED_STAKE_CHANGED);
        assertThat(result.decision().finalStake()).isEqualByComparingTo("12.00");
        assertThat(result.decision().observedCount()).isEqualTo(2);
        assertThat(repository.list(databasePath, null, null)).hasSize(1);
    }

    @Test
    void decisionReasonChangeUpdatesRowAndReturnsReasonChanged() {
        String databasePath = tempDir.resolve("stake-sizing-reason-change.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);
        StakeSizingShadowDecision first = decision("rec-1", StakeSizingMode.TIERED_CONFIDENCE, StakeSizingRiskProfile.BALANCED)
            .withLatest(new BigDecimal("10.00"), new BigDecimal("10.00"), false, null, StakeSizingDecisionReason.CONFIDENCE_NOT_AVAILABLE, "[]", new BigDecimal("2.50"), Instant.parse("2026-07-01T10:00:00Z"));
        StakeSizingShadowDecision second = first.withLatest(
            first.calculatedStake(),
            first.finalStake(),
            false,
            null,
            StakeSizingDecisionReason.CONFIDENCE_SCORE,
            first.adjustmentSummary(),
            first.odds(),
            Instant.parse("2026-07-01T11:00:00Z")
        );

        repository.upsert(databasePath, first);
        StakeSizingShadowDecisionUpsertResult result = repository.upsert(databasePath, second);

        assertThat(result.action()).isEqualTo(StakeSizingShadowDecisionUpsertAction.UPDATED_REASON_CHANGED);
        assertThat(result.decision().decisionReason()).isEqualTo(StakeSizingDecisionReason.CONFIDENCE_SCORE);
        assertThat(repository.list(databasePath, null, null)).hasSize(1);
    }

    @Test
    void blockDecisionChangeUpdatesRowAndReturnsDecisionChanged() {
        String databasePath = tempDir.resolve("stake-sizing-decision-change.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);
        StakeSizingShadowDecision first = decision("rec-1", StakeSizingMode.FRACTIONAL_KELLY_SHADOW, StakeSizingRiskProfile.CONSERVATIVE);
        StakeSizingShadowDecision second = first.withLatest(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            StakeSizingBlockReason.NOT_AVAILABLE,
            StakeSizingDecisionReason.PROBABILITY_NOT_AVAILABLE,
            first.adjustmentSummary(),
            first.odds(),
            Instant.parse("2026-07-01T11:00:00Z")
        );

        repository.upsert(databasePath, first);
        StakeSizingShadowDecisionUpsertResult result = repository.upsert(databasePath, second);

        assertThat(result.action()).isEqualTo(StakeSizingShadowDecisionUpsertAction.UPDATED_DECISION_CHANGED);
        assertThat(result.decision().wouldBlock()).isTrue();
        assertThat(result.decision().blockReason()).isEqualTo(StakeSizingBlockReason.NOT_AVAILABLE);
        assertThat(repository.list(databasePath, null, null)).hasSize(1);
    }

    @Test
    void differentPolicyRiskProfileOrSourceCreatesDifferentRows() {
        String databasePath = tempDir.resolve("stake-sizing-multiple.db").toString();
        JdbcStakeSizingShadowDecisionRepository repository = new JdbcStakeSizingShadowDecisionRepository(databasePath);

        repository.upsert(databasePath, decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.BALANCED));
        repository.upsert(databasePath, decision("rec-1", StakeSizingMode.RISK_ADJUSTED, StakeSizingRiskProfile.BALANCED));
        repository.upsert(databasePath, decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.CONSERVATIVE));
        repository.upsert(databasePath, decision("rec-1", StakeSizingMode.FLAT, StakeSizingRiskProfile.BALANCED, StakeSizingSource.DIAGNOSTIC));

        assertThat(repository.list(databasePath, null, null)).hasSize(4);
        assertThat(repository.countDuplicateLogicalKeys(databasePath)).isZero();
    }

    private static StakeSizingShadowDecision decision(
        String recommendationId,
        StakeSizingMode policyName,
        StakeSizingRiskProfile riskProfile
    ) {
        return decision(recommendationId, policyName, riskProfile, StakeSizingSource.SHADOW);
    }

    private static StakeSizingShadowDecision decision(
        String recommendationId,
        StakeSizingMode policyName,
        StakeSizingRiskProfile riskProfile,
        StakeSizingSource source
    ) {
        return new StakeSizingShadowDecision(
            recommendationId + "-" + policyName.name() + "-" + riskProfile.name() + "-" + source.name(),
            recommendationId,
            "betfair|1.234|42|HOME|value-football",
            policyName,
            riskProfile,
            source,
            SelectionSide.HOME,
            new BigDecimal("2.50"),
            "value-football",
            new BigDecimal("10.00"),
            new BigDecimal("1.00"),
            new BigDecimal("100.00"),
            new BigDecimal("500.00"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            false,
            null,
            StakeSizingDecisionReason.BASE_STAKE,
            "[]",
            Instant.parse("2026-07-01T10:00:00Z"),
            Instant.parse("2026-07-01T10:00:00Z"),
            Instant.parse("2026-07-01T10:00:00Z"),
            1
        );
    }
}
