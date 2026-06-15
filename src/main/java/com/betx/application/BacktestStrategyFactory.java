package com.betx.application;

import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Creates the research strategies supported by the historical backtest engine. */
public final class BacktestStrategyFactory {
    public static final long DEFAULT_RANDOM_SEED = 42L;
    public static final List<String> STRATEGY_IDS = List.of(
        ValueFootballSignalStrategy.STRATEGY_NAME,
        "value-football-draw-only",
        "favorite",
        "home-favorite",
        "away-underdog",
        "draw",
        "random"
    );

    private BacktestStrategyFactory() {
    }

    public static List<BacktestStrategy> all(long randomSeed) {
        return List.of(
            valueFootball(new EventMarketAnalyzer()),
            valueFootballDrawOnly(new EventMarketAnalyzer()),
            new FavoriteStrategy(),
            new HomeFavoriteStrategy(),
            new AwayUnderdogStrategy(),
            new DrawStrategy(),
            new RandomStrategy(randomSeed)
        );
    }

    public static BacktestStrategy valueFootball(EventMarketAnalyzer analyzer) {
        return new ValueFootballBacktestStrategy(analyzer);
    }

    public static BacktestStrategy valueFootballDrawOnly(EventMarketAnalyzer analyzer) {
        return new ValueFootballDrawOnlyBacktestStrategy(analyzer);
    }

    private static boolean validBenchmarkMarket(BacktestInputRow row, BetxConfig config) {
        return row.bestBackPrice() != null
            && row.bestLayPrice() != null
            && row.spread() != null
            && row.liquidity().compareTo(config.risk().maxStake()) >= 0;
    }

    private static Optional<BacktestInputRow> favorite(List<BacktestInputRow> rows) {
        return rows.stream()
            .filter(row -> row.bestBackPrice() != null)
            .min(Comparator.comparing(BacktestInputRow::bestBackPrice)
                .thenComparing(BacktestInputRow::selectionId));
    }

    private static BacktestStrategyDecision benchmarkDecision(String reason) {
        return new BacktestStrategyDecision(reason, "Benchmark", null);
    }

    private abstract static class MarketOnceStrategy implements BacktestStrategy {
        @Override
        public boolean enabled(BetxConfig config) {
            return true;
        }

        @Override
        public String tradeKey(BacktestInputRow row) {
            return row.exchange() + "|" + row.marketId();
        }
    }

    private static final class ValueFootballBacktestStrategy implements BacktestStrategy {
        private final EventMarketAnalyzer analyzer;

        private ValueFootballBacktestStrategy(EventMarketAnalyzer analyzer) {
            this.analyzer = analyzer;
        }

        @Override
        public String id() {
            return ValueFootballSignalStrategy.STRATEGY_NAME;
        }

        @Override
        public boolean enabled(BetxConfig config) {
            return config.strategies().stream()
                .anyMatch(strategy -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategy.name()) && strategy.enabled());
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            return evaluateWithDiagnostics(row, marketObservationRows, recentSnapshots, config, BacktestDatasetCapability.EXCHANGE_SNAPSHOTS)
                .decision();
        }

        @Override
        public BacktestStrategyEvaluation evaluateWithDiagnostics(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config,
            BacktestDatasetCapability datasetCapability
        ) {
            Optional<StrategyConfig> strategyConfig = config.strategies().stream()
                .filter(strategy -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategy.name()))
                .findFirst();
            if (strategyConfig.isEmpty() || !strategyConfig.get().enabled()) {
                return BacktestStrategyEvaluation.skipped();
            }
            RunnerAnalysis analysis = analyzer.analyze(
                row.toMarketSnapshot(),
                recentSnapshots,
                strategyConfig.get(),
                config.risk()
            );
            if (analysis.recommendation() != RecommendationType.BET) {
                return BacktestStrategyEvaluation.rejected(rejectionReason(analysis, recentSnapshots, datasetCapability, row));
            }
            return BacktestStrategyEvaluation.decision(new BacktestStrategyDecision(
                analysis.reason(),
                analysis.score().confidenceLabel(),
                recentSnapshots.isEmpty()
                    ? null
                    : percentageDelta(recentSnapshots.getFirst().snapshot().bestBackPrice(), analysis.bestBackPrice())
            ));
        }
    }

    private static final class ValueFootballDrawOnlyBacktestStrategy implements BacktestStrategy {
        private final ValueFootballBacktestStrategy delegate;

        private ValueFootballDrawOnlyBacktestStrategy(EventMarketAnalyzer analyzer) {
            this.delegate = new ValueFootballBacktestStrategy(analyzer);
        }

        @Override
        public String id() {
            return "value-football-draw-only";
        }

        @Override
        public boolean enabled(BetxConfig config) {
            return delegate.enabled(config);
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            return evaluateWithDiagnostics(row, marketObservationRows, recentSnapshots, config, BacktestDatasetCapability.EXCHANGE_SNAPSHOTS)
                .decision();
        }

        @Override
        public BacktestStrategyEvaluation evaluateWithDiagnostics(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config,
            BacktestDatasetCapability datasetCapability
        ) {
            if (BacktestRunnerType.fromSelectionId(row.selectionId()) != BacktestRunnerType.DRAW) {
                return BacktestStrategyEvaluation.skipped();
            }
            return delegate.evaluateWithDiagnostics(row, marketObservationRows, recentSnapshots, config, datasetCapability);
        }
    }

    private static final class FavoriteStrategy extends MarketOnceStrategy {
        @Override
        public String id() {
            return "favorite";
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            if (!validBenchmarkMarket(row, config)) {
                return Optional.empty();
            }
            return favorite(marketObservationRows)
                .filter(favorite -> favorite.selectionId() == row.selectionId())
                .map(ignored -> benchmarkDecision("favorite"));
        }
    }

    private static final class HomeFavoriteStrategy extends MarketOnceStrategy {
        @Override
        public String id() {
            return "home-favorite";
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            if (!validBenchmarkMarket(row, config) || BacktestRunnerType.fromSelectionId(row.selectionId()) != BacktestRunnerType.HOME) {
                return Optional.empty();
            }
            return favorite(marketObservationRows)
                .filter(favorite -> favorite.selectionId() == row.selectionId())
                .map(ignored -> benchmarkDecision("home_favorite"));
        }
    }

    private static final class AwayUnderdogStrategy extends MarketOnceStrategy {
        @Override
        public String id() {
            return "away-underdog";
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            if (!validBenchmarkMarket(row, config) || BacktestRunnerType.fromSelectionId(row.selectionId()) != BacktestRunnerType.AWAY) {
                return Optional.empty();
            }
            return favorite(marketObservationRows)
                .filter(favorite -> favorite.selectionId() != row.selectionId())
                .map(ignored -> benchmarkDecision("away_underdog"));
        }
    }

    private static final class DrawStrategy extends MarketOnceStrategy {
        @Override
        public String id() {
            return "draw";
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            if (!validBenchmarkMarket(row, config) || BacktestRunnerType.fromSelectionId(row.selectionId()) != BacktestRunnerType.DRAW) {
                return Optional.empty();
            }
            return Optional.of(benchmarkDecision("draw"));
        }
    }

    private static final class RandomStrategy extends MarketOnceStrategy {
        private final long seed;

        private RandomStrategy(long seed) {
            this.seed = seed;
        }

        @Override
        public String id() {
            return "random";
        }

        @Override
        public Optional<BacktestStrategyDecision> evaluate(
            BacktestInputRow row,
            List<BacktestInputRow> marketObservationRows,
            List<ObservedMarketSnapshot> recentSnapshots,
            BetxConfig config
        ) {
            List<BacktestInputRow> candidates = marketObservationRows.stream()
                .filter(candidate -> validBenchmarkMarket(candidate, config))
                .sorted(Comparator.comparingLong(BacktestInputRow::selectionId))
                .toList();
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            int selected = new Random(seed ^ row.marketId().hashCode()).nextInt(candidates.size());
            BacktestInputRow selectedRow = candidates.get(selected);
            if (selectedRow.selectionId() != row.selectionId()) {
                return Optional.empty();
            }
            return Optional.of(benchmarkDecision("random_seed_" + seed));
        }
    }

    private static BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(8, RoundingMode.HALF_UP);
    }

    private static String rejectionReason(
        RunnerAnalysis analysis,
        List<ObservedMarketSnapshot> recentSnapshots,
        BacktestDatasetCapability datasetCapability,
        BacktestInputRow row
    ) {
        if (datasetCapability == BacktestDatasetCapability.SINGLE_PRICE && (recentSnapshots == null || recentSnapshots.isEmpty())) {
            return "insufficient_history";
        }
        if ("unknown".equals(row.oddsSource())) {
            return "unsupported_synthetic_observation";
        }
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return "insufficient_history";
        }
        if (recentSnapshots.getFirst().snapshot().bestBackPrice() == null) {
            return "missing_previous_price";
        }
        String reason = analysis.reason();
        if (reason.contains("missing_back_or_lay_price")) {
            return "missing_previous_price";
        }
        if (reason.contains("liquidity_below_minimum")) {
            return "liquidity_filter";
        }
        if (reason.contains("spread_above_threshold")) {
            return "spread_filter";
        }
        if (reason.contains("odds_out_of_range")
            || reason.contains("draw_runner_not_supported")
            || reason.contains("away_runner_value_profile_missing")) {
            return "odds_movement_filter";
        }
        if (analysis.recommendation() == RecommendationType.WATCH || reason.contains("score_below_threshold")) {
            return "confidence_threshold";
        }
        return reason;
    }
}
