package com.betx.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ValueFootballSignalStrategyTest {
    private final ValueFootballSignalStrategy strategy = new ValueFootballSignalStrategy();

    @Test
    void acceptsLiquidMarketWithTightSpreadAndValidOdds() {
        MarketSnapshot snapshot = snapshot(BigDecimal.valueOf(1_200), BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.60));
        StrategyConfig strategyConfig = new StrategyConfig("value-football", true, BigDecimal.valueOf(0.06), BigDecimal.valueOf(500));
        RiskConfig riskConfig = new RiskConfig(BigDecimal.valueOf(7), BigDecimal.valueOf(25), 3, false);

        SignalDecision decision = strategy.evaluate(snapshot, strategyConfig, riskConfig);

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.reason()).isEqualTo("liquidity_ok, spread_ok, odds_range_ok, dry_run_only");
        assertThat(decision.signal()).contains(
            new BetSignal("betfair", "1.234", 42L, BetSide.BACK, BigDecimal.valueOf(2.50), BigDecimal.valueOf(7), decision.reason(), "dry-run")
        );
    }

    @Test
    void rejectsLowLiquidityMarkets() {
        MarketSnapshot snapshot = snapshot(BigDecimal.valueOf(200), BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.60));
        StrategyConfig strategyConfig = new StrategyConfig("value-football", true, BigDecimal.valueOf(0.06), BigDecimal.valueOf(500));
        RiskConfig riskConfig = new RiskConfig(BigDecimal.valueOf(7), BigDecimal.valueOf(25), 3, false);

        SignalDecision decision = strategy.evaluate(snapshot, strategyConfig, riskConfig);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).isEqualTo("liquidity_below_minimum");
        assertThat(decision.signal()).isEmpty();
    }

    @Test
    void rejectsWideSpreadMarkets() {
        MarketSnapshot snapshot = snapshot(BigDecimal.valueOf(1_200), BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.80));
        StrategyConfig strategyConfig = new StrategyConfig("value-football", true, BigDecimal.valueOf(0.06), BigDecimal.valueOf(500));
        RiskConfig riskConfig = new RiskConfig(BigDecimal.valueOf(7), BigDecimal.valueOf(25), 3, false);

        SignalDecision decision = strategy.evaluate(snapshot, strategyConfig, riskConfig);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reason()).isEqualTo("spread_above_threshold");
        assertThat(decision.signal()).isEmpty();
    }

    private MarketSnapshot snapshot(BigDecimal liquidity, BigDecimal bestBackPrice, BigDecimal bestLayPrice) {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            bestBackPrice,
            bestLayPrice,
            bestLayPrice.subtract(bestBackPrice).divide(bestBackPrice),
            liquidity
        );
    }
}
