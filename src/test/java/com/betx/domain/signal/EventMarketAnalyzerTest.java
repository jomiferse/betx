package com.betx.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventMarketAnalyzerTest {
    private final EventMarketAnalyzer analyzer = new EventMarketAnalyzer();
    private final StrategyConfig strategyConfig = new StrategyConfig("value-football", true, new BigDecimal("0.06"), new BigDecimal("500"));
    private final RiskConfig riskConfig = new RiskConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3);

    @Test
    void rejectsTestMarkets() {
        RunnerAnalysis analysis = analyzer.analyze(snapshot("Test C v Test V", "Runner A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500")), Optional.empty(), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.NO_BET);
        assertThat(analysis.reason()).isEqualTo("test_market");
    }

    @Test
    void rejectsRunnersWithoutBackOrLayPrice() {
        RunnerAnalysis analysis = analyzer.analyze(snapshot("Team A v Team B", "Runner A", null, new BigDecimal("2.60"), new BigDecimal("1500")), Optional.empty(), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.NO_BET);
        assertThat(analysis.reason()).isEqualTo("missing_back_or_lay_price");
    }

    @Test
    void watchesValidRunnerWithoutPreviousSnapshot() {
        RunnerAnalysis analysis = analyzer.analyze(snapshot("Team A v Team B", "Runner A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500")), Optional.empty(), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.WATCH);
        assertThat(analysis.reason()).isEqualTo("valid_market_waiting_for_movement");
    }

    @Test
    void recommendsBetForFavorableBackOddsMovement() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Runner A", new BigDecimal("2.60"), new BigDecimal("2.70"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Runner A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("liquidity_ok", "spread_ok", "odds_range_ok", "favorable_odds_movement", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void recommendsBetForFavorableLiquidityMovement() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Runner A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1000"));
        MarketSnapshot current = snapshot("Team A v Team B", "Runner A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1030"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("liquidity_ok", "spread_ok", "odds_range_ok", "favorable_liquidity_movement", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void rejectsDrawRunnersInMatchOddsMarkets() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Draw", new BigDecimal("3.30"), new BigDecimal("3.43"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Draw", new BigDecimal("3.20"), new BigDecimal("3.33"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.NO_BET);
        assertThat(analysis.reason()).isEqualTo("draw_runner_not_supported");
    }

    @Test
    void recommendsBetForStableDrawRunnersInMatchOddsMarkets() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Draw", new BigDecimal("3.30"), new BigDecimal("3.43"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Draw", new BigDecimal("3.30"), new BigDecimal("3.43"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("stable_draw_profile", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void boostsHomeRunnersInMatchOddsMarkets() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Team A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Team A", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("home_runner_profile", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void rejectsAwayRunnersWithoutValueProfile() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Team B", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Team B", new BigDecimal("2.45"), new BigDecimal("2.55"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.NO_BET);
        assertThat(analysis.reason()).isEqualTo("away_runner_value_profile_missing");
    }

    @Test
    void recommendsBetForAwayRunnersWithValueProfile() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Team B", new BigDecimal("3.30"), new BigDecimal("3.43"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Team B", new BigDecimal("3.20"), new BigDecimal("3.33"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("away_value_profile", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void boostsStableMidOddsAwayRunnersWithValueProfile() {
        MarketSnapshot previous = snapshot("Team A v Team B", "Team B", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));
        MarketSnapshot current = snapshot("Team A v Team B", "Team B", new BigDecimal("2.50"), new BigDecimal("2.60"), new BigDecimal("1500"));

        RunnerAnalysis analysis = analyzer.analyze(current, Optional.of(previous), strategyConfig, riskConfig);

        assertThat(analysis.recommendation()).isEqualTo(RecommendationType.BET);
        assertThat(analysis.reason()).contains("away_value_profile", "dry_run_only");
        assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70);
    }

    private MarketSnapshot snapshot(String eventName, String runnerName, BigDecimal back, BigDecimal lay, BigDecimal liquidity) {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            eventName,
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            runnerName,
            back,
            lay,
            back == null || lay == null ? null : lay.subtract(back).divide(back, 8, RoundingMode.HALF_UP),
            liquidity
        );
    }
}
