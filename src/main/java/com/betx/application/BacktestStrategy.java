package com.betx.application;

import com.betx.domain.config.BetxConfig;
import com.betx.domain.signal.ObservedMarketSnapshot;
import java.util.List;
import java.util.Optional;

/** Research strategy that can be replayed by the shared historical backtest engine. */
public interface BacktestStrategy {
    String id();

    boolean enabled(BetxConfig config);

    Optional<BacktestStrategyDecision> evaluate(
        BacktestInputRow row,
        List<BacktestInputRow> marketObservationRows,
        List<ObservedMarketSnapshot> recentSnapshots,
        BetxConfig config
    );

    default BacktestStrategyEvaluation evaluateWithDiagnostics(
        BacktestInputRow row,
        List<BacktestInputRow> marketObservationRows,
        List<ObservedMarketSnapshot> recentSnapshots,
        BetxConfig config,
        BacktestDatasetCapability datasetCapability
    ) {
        return new BacktestStrategyEvaluation(evaluate(row, marketObservationRows, recentSnapshots, config), null);
    }

    default String tradeKey(BacktestInputRow row) {
        return row.exchange() + "|" + row.marketId() + "|" + row.selectionId();
    }
}
