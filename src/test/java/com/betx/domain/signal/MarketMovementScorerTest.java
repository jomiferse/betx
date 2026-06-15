package com.betx.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketMovementScorerTest {
    private final EventMarketAnalyzer analyzer = new EventMarketAnalyzer();
    private final StrategyConfig strategyConfig = new StrategyConfig("value-football", true, new BigDecimal("0.06"), new BigDecimal("500"));
    private final RiskConfig riskConfig = new RiskConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3);

    @Test
    void scoresHighForOddsDropLiquidityRisePersistenceAndLowVolatility() {
        RunnerAnalysis analysis = analyzer.analyze(
            snapshot("1.88", "1.95", "0.03723404", "1380"),
            List.of(
                observed("2026-05-31T10:02:00Z", "1.90", "1.97", "1300"),
                observed("2026-05-31T10:01:00Z", "1.96", "2.04", "1180"),
                observed("2026-05-31T10:00:00Z", "2.10", "2.18", "1000")
            ),
            strategyConfig,
            riskConfig
        );

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(80);
        assertThat(analysis.score().confidenceLabel()).isEqualTo("High confidence");
        assertThat(analysis.score().reasons())
            .contains("Odds moved from 1.90 -> 1.88", "Liquidity increased +6.15%", "Movement persisted for 3 cycles", "Volatility is low");
    }

    @Test
    void scoresMediumForPartialMovementAndKeepsWatching() {
        RunnerAnalysis analysis = analyzer.analyze(
            snapshot("2.48", "2.58", "0.04032258", "1220"),
            List.of(observed("2026-05-31T10:00:00Z", "2.50", "2.60", "1200")),
            strategyConfig,
            riskConfig
        );

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.WATCH);
        assertThat(analysis.score().value()).isBetween(40, 69);
        assertThat(analysis.score().confidenceLabel()).isEqualTo("Medium confidence");
    }

    @Test
    void usesConfiguredOddsDropThresholdForFavorableMovement() {
        EventMarketAnalyzer stricterAnalyzer = new EventMarketAnalyzer(new BigDecimal("-3.00"));

        RunnerAnalysis analysis = stricterAnalyzer.analyze(
            snapshot("2.45", "2.55", "0.04081633", "1200"),
            List.of(observed("2026-05-31T10:00:00Z", "2.50", "2.60", "1200")),
            strategyConfig,
            riskConfig
        );

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.WATCH);
        assertThat(analysis.reason()).doesNotContain("favorable_odds_movement");
    }

    @Test
    void rejectsInvalidBaseQualityRegardlessOfMovement() {
        RunnerAnalysis analysis = analyzer.analyze(
            snapshot("2.00", "2.08", "0.04000000", "250"),
            List.of(observed("2026-05-31T10:00:00Z", "2.40", "2.50", "1000")),
            strategyConfig,
            riskConfig
        );

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.NO_BET);
        assertThat(analysis.score().value()).isZero();
        assertThat(analysis.reason()).isEqualTo("liquidity_below_minimum");
    }

    @Test
    void penalizesHighRecentVolatility() {
        RunnerAnalysis analysis = analyzer.analyze(
            snapshot("1.88", "1.95", "0.03723404", "1380"),
            List.of(
                observed("2026-05-31T10:02:00Z", "2.30", "2.40", "1300"),
                observed("2026-05-31T10:01:00Z", "1.70", "1.80", "1180"),
                observed("2026-05-31T10:00:00Z", "2.10", "2.18", "1000")
            ),
            strategyConfig,
            riskConfig
        );

        assertThat(analysis.score().value()).isLessThan(70);
        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.WATCH);
        assertThat(analysis.score().reasons()).contains("Volatility is high");
    }

    private ObservedMarketSnapshot observed(String observedAt, String back, String lay, String liquidity) {
        return new ObservedMarketSnapshot(Instant.parse(observedAt), snapshot(back, lay, spread(back, lay), liquidity));
    }

    private MarketSnapshot snapshot(String back, String lay, String spread, String liquidity) {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            "Runner A",
            new BigDecimal(back),
            new BigDecimal(lay),
            new BigDecimal(spread),
            new BigDecimal(liquidity)
        );
    }

    private String spread(String back, String lay) {
        return new BigDecimal(lay).subtract(new BigDecimal(back))
            .divide(new BigDecimal(back), 8, RoundingMode.HALF_UP)
            .toPlainString();
    }
}
