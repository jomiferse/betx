package com.betx.domain.signal;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/** Technical market analyzer for dry-run event recommendations. */
public class EventMarketAnalyzer {
    private static final BigDecimal MAX_RELATIVE_SPREAD = BigDecimal.valueOf(0.08);
    private static final BigDecimal MIN_BACK_ODDS = BigDecimal.valueOf(1.5);
    private static final BigDecimal MAX_BACK_ODDS = BigDecimal.valueOf(6.0);
    private static final BigDecimal FAVORABLE_BACK_DROP_PERCENT = BigDecimal.valueOf(-1.0);
    private static final BigDecimal FAVORABLE_LIQUIDITY_RISE_PERCENT = BigDecimal.valueOf(2.0);

    public RunnerAnalysis analyze(
        MarketSnapshot snapshot,
        Optional<MarketSnapshot> previousSnapshot,
        StrategyConfig strategyConfig,
        RiskConfig riskConfig
    ) {
        if (isTestMarket(snapshot)) {
            return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, "test_market");
        }
        if (snapshot.bestBackPrice() == null || snapshot.bestLayPrice() == null) {
            return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, "missing_back_or_lay_price");
        }
        if (snapshot.liquidity().compareTo(strategyConfig.minLiquidity()) < 0) {
            return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, "liquidity_below_minimum");
        }
        if (snapshot.spread() == null || snapshot.spread().compareTo(MAX_RELATIVE_SPREAD) > 0) {
            return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, "spread_above_threshold");
        }
        if (snapshot.bestBackPrice().compareTo(MIN_BACK_ODDS) < 0 || snapshot.bestBackPrice().compareTo(MAX_BACK_ODDS) > 0) {
            return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, "odds_out_of_range");
        }
        if (previousSnapshot.isEmpty()) {
            return RunnerAnalysis.from(snapshot, RecommendationType.WATCH, "valid_market_waiting_for_movement");
        }

        MarketSnapshot previous = previousSnapshot.get();
        BigDecimal backMovement = percentageDelta(previous.bestBackPrice(), snapshot.bestBackPrice());
        if (backMovement != null && backMovement.compareTo(FAVORABLE_BACK_DROP_PERCENT) <= 0) {
            return RunnerAnalysis.from(snapshot, RecommendationType.BET, "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only");
        }
        BigDecimal liquidityMovement = percentageDelta(previous.liquidity(), snapshot.liquidity());
        if (liquidityMovement != null && liquidityMovement.compareTo(FAVORABLE_LIQUIDITY_RISE_PERCENT) >= 0) {
            return RunnerAnalysis.from(snapshot, RecommendationType.BET, "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only");
        }
        return RunnerAnalysis.from(snapshot, RecommendationType.WATCH, "valid_market_waiting_for_movement");
    }

    public boolean isTestMarket(MarketSnapshot snapshot) {
        return containsTest(snapshot.marketName()) || containsTest(snapshot.eventName()) || containsTest(snapshot.competitionName());
    }

    private boolean containsTest(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("test");
    }

    private BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(8, RoundingMode.HALF_UP);
    }
}
