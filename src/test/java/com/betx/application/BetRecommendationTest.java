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
            BetRecommendationStatus.CREATED,
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
        assertThat(recommendation.status()).isEqualTo(BetRecommendationStatus.CREATED);
    }
}
