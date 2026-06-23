package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BetRecommendationTest {
    @Test
    void createsShadowRecommendationWithStableMinimumFields() {
        Instant observedAt = Instant.parse("2026-06-22T08:25:14Z");
        Instant recommendedAt = Instant.parse("2026-06-22T08:25:15Z");
        BetRecommendation recommendation = new BetRecommendation(
            "rec-1",
            "eval-1",
            "betfair",
            "1.234",
            42L,
            SelectionSide.HOME,
            "Team A v Team B",
            "Team A",
            "Premier League",
            Instant.parse("2026-06-22T20:00:00Z"),
            "value-football",
            new BigDecimal("2.50"),
            observedAt,
            recommendedAt,
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            recommendedAt,
            null,
            null,
            new BigDecimal("1200"),
            "liquidity_ok"
        );

        assertThat(recommendation.id()).isEqualTo("rec-1");
        assertThat(recommendation.evaluationId()).isEqualTo("eval-1");
        assertThat(recommendation.selectionSide()).isEqualTo(SelectionSide.HOME);
        assertThat(recommendation.strategyName()).isEqualTo("value-football");
        assertThat(recommendation.recommendedOdds()).isEqualByComparingTo("2.50");
        assertThat(recommendation.observedAt()).isEqualTo(observedAt);
        assertThat(recommendation.recommendedAt()).isEqualTo(recommendedAt);
        assertThat(recommendation.createdAt()).isEqualTo(recommendedAt);
        assertThat(recommendation.confidence()).isNull();
        assertThat(recommendation.edge()).isNull();
        assertThat(recommendation.source()).isEqualTo(BetRecommendationSource.SHADOW);
        assertThat(recommendation.status()).isEqualTo(BetRecommendationStatus.ACTIVE);
        assertThat(recommendation.canonicalKey()).isEqualTo("betfair|1.234|42|HOME|value-football");
        assertThat(recommendation.firstSeenAt()).isEqualTo(observedAt);
        assertThat(recommendation.lastSeenAt()).isEqualTo(observedAt);
        assertThat(recommendation.observedCount()).isEqualTo(1);
        assertThat(recommendation.initialRecommendedOdds()).isEqualByComparingTo("2.50");
        assertThat(recommendation.latestRecommendedOdds()).isEqualByComparingTo("2.50");
        assertThat(recommendation.bestRecommendedOdds()).isEqualByComparingTo("2.50");
        assertThat(recommendation.lastEvaluationId()).isEqualTo("eval-1");
    }

    @Test
    void canonicalKeyDoesNotUseTimestampsEvaluationIdSourceOrMarketMetadata() {
        Instant firstObservedAt = Instant.parse("2026-06-22T08:25:14Z");
        Instant secondObservedAt = Instant.parse("2026-06-22T09:30:00Z");
        BetRecommendation first = new BetRecommendation(
            "rec-1",
            "eval-1",
            "BETFAIR",
            "1.234",
            42L,
            SelectionSide.HOME,
            "Team A v Team B",
            "Team A",
            "Premier League",
            Instant.parse("2026-06-22T20:00:00Z"),
            "value-football",
            new BigDecimal("2.50"),
            firstObservedAt,
            firstObservedAt,
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            firstObservedAt,
            null,
            null,
            new BigDecimal("1200"),
            "liquidity_ok"
        );
        BetRecommendation second = new BetRecommendation(
            "rec-2",
            "eval-2",
            "betfair",
            "1.234",
            42L,
            SelectionSide.HOME,
            "Different name",
            "Different runner",
            "Different competition",
            Instant.parse("2026-06-23T20:00:00Z"),
            "value-football",
            new BigDecimal("2.60"),
            secondObservedAt,
            secondObservedAt,
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            secondObservedAt,
            null,
            null,
            new BigDecimal("1300"),
            "new_eval"
        );

        assertThat(first.canonicalKey()).isEqualTo(second.canonicalKey());
    }

    @Test
    void canonicalKeyDiffersByStrategicOpportunityFields() {
        BetRecommendation base = recommendation("betfair", "1.234", 42L, SelectionSide.HOME, "value-football");

        assertThat(base.canonicalKey()).isNotEqualTo(recommendation("smarkets", "1.234", 42L, SelectionSide.HOME, "value-football").canonicalKey());
        assertThat(base.canonicalKey()).isNotEqualTo(recommendation("betfair", "1.999", 42L, SelectionSide.HOME, "value-football").canonicalKey());
        assertThat(base.canonicalKey()).isNotEqualTo(recommendation("betfair", "1.234", 99L, SelectionSide.HOME, "value-football").canonicalKey());
        assertThat(base.canonicalKey()).isNotEqualTo(recommendation("betfair", "1.234", 42L, SelectionSide.DRAW, "value-football").canonicalKey());
        assertThat(base.canonicalKey()).isNotEqualTo(recommendation("betfair", "1.234", 42L, SelectionSide.HOME, "other-strategy").canonicalKey());
    }

    private static BetRecommendation recommendation(
        String exchange,
        String marketId,
        long selectionId,
        SelectionSide side,
        String strategyName
    ) {
        Instant observedAt = Instant.parse("2026-06-22T08:25:14Z");
        return new BetRecommendation(
            "rec-1",
            "eval-1",
            exchange,
            marketId,
            selectionId,
            side,
            "Team A v Team B",
            "Team A",
            "Premier League",
            Instant.parse("2026-06-22T20:00:00Z"),
            strategyName,
            new BigDecimal("2.50"),
            observedAt,
            observedAt,
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.ACTIVE,
            observedAt,
            null,
            null,
            new BigDecimal("1200"),
            "liquidity_ok"
        );
    }
}
