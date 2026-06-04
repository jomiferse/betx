package com.betx.domain.signal;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;

/** MVP football strategy that emits dry-run BACK signals from simple market filters. */
public class ValueFootballSignalStrategy {
    public static final String STRATEGY_NAME = "value-football";
    public static final String ACCEPTED_REASON = "liquidity_ok, spread_ok, odds_range_ok, dry_run_only";

    private static final BigDecimal MAX_RELATIVE_SPREAD = BigDecimal.valueOf(0.08);
    private static final BigDecimal MIN_BACK_ODDS = BigDecimal.valueOf(1.5);
    private static final BigDecimal MAX_BACK_ODDS = BigDecimal.valueOf(6.0);

    /** Evaluates one market snapshot against the MVP strategy rules. */
    public SignalDecision evaluate(MarketSnapshot snapshot, StrategyConfig strategyConfig, RiskConfig riskConfig) {
        if (!STRATEGY_NAME.equals(strategyConfig.name()) || !strategyConfig.enabled()) {
            return SignalDecision.rejected("strategy_disabled");
        }
        if (snapshot.liquidity().compareTo(strategyConfig.minLiquidity()) < 0) {
            return SignalDecision.rejected("liquidity_below_minimum");
        }
        if (snapshot.bestBackPrice() == null || snapshot.bestLayPrice() == null) {
            return SignalDecision.rejected("missing_back_or_lay_price");
        }
        if (snapshot.spread() == null || snapshot.spread().compareTo(MAX_RELATIVE_SPREAD) > 0) {
            return SignalDecision.rejected("spread_above_threshold");
        }
        if (snapshot.bestBackPrice().compareTo(MIN_BACK_ODDS) < 0 || snapshot.bestBackPrice().compareTo(MAX_BACK_ODDS) > 0) {
            return SignalDecision.rejected("odds_out_of_range");
        }

        BetSignal signal = new BetSignal(
            snapshot.exchange(),
            snapshot.marketId(),
            snapshot.selectionId(),
            BetSide.BACK,
            snapshot.bestBackPrice(),
            riskConfig.maxStake(),
            ACCEPTED_REASON,
            "dry-run"
        );
        return SignalDecision.accepted(signal);
    }
}
