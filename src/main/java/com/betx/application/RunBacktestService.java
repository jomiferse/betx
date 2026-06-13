package com.betx.application;

import com.betx.application.port.out.BacktestHistoryReader;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Replays normalized historical rows through the current signal analyzer. */
@Service
public class RunBacktestService {
    private static final int RECENT_SNAPSHOT_LIMIT = 10;

    private final BetxConfigRepository configRepository;
    private final BacktestHistoryReader historyReader;
    private final EventMarketAnalyzer analyzer;

    @Autowired
    public RunBacktestService(BetxConfigRepository configRepository, BacktestHistoryReader historyReader) {
        this.configRepository = configRepository;
        this.historyReader = historyReader;
        this.analyzer = new EventMarketAnalyzer();
    }

    public BacktestResult run(ConfigPath configPath, Path inputPath) {
        BetxConfig config = configRepository.load(configPath);
        List<BacktestInputRow> rows = historyReader.read(inputPath);
        Optional<StrategyConfig> strategyConfig = valueFootballStrategy(config);
        if (strategyConfig.isEmpty() || !strategyConfig.get().enabled()) {
            return BacktestResult.from(rows.size(), 0, List.of());
        }

        List<BacktestInputRow> orderedRows = rows.stream()
            .sorted(Comparator.comparing(BacktestInputRow::observedAt))
            .toList();
        Map<String, ArrayDeque<ObservedMarketSnapshot>> historyByRunner = new HashMap<>();
        Set<String> tradedRunners = new HashSet<>();
        List<BacktestTrade> trades = new ArrayList<>();
        int runnersAnalyzed = 0;

        for (BacktestInputRow row : orderedRows) {
            String key = runnerKey(row);
            ArrayDeque<ObservedMarketSnapshot> recent = historyByRunner.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            RunnerAnalysis analysis = analyzer.analyze(
                row.toMarketSnapshot(),
                List.copyOf(recent),
                strategyConfig.get(),
                config.risk()
            );
            runnersAnalyzed++;
            if (analysis.recommendation() == RecommendationType.BET && tradedRunners.add(key)) {
                trades.add(toTrade(row, analysis, config.risk().maxStake(), Optional.ofNullable(recent.peekFirst())));
            }
            recent.addFirst(new ObservedMarketSnapshot(row.observedAt(), row.toMarketSnapshot()));
            while (recent.size() > RECENT_SNAPSHOT_LIMIT) {
                recent.removeLast();
            }
        }

        return BacktestResult.from(rows.size(), runnersAnalyzed, trades);
    }

    private Optional<StrategyConfig> valueFootballStrategy(BetxConfig config) {
        return config.strategies().stream()
            .filter(strategyConfig -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategyConfig.name()))
            .findFirst();
    }

    private BacktestTrade toTrade(
        BacktestInputRow row,
        RunnerAnalysis analysis,
        BigDecimal stake,
        Optional<ObservedMarketSnapshot> previousSnapshot
    ) {
        BigDecimal profitLoss = row.outcome() == BacktestOutcome.WIN
            ? stake.multiply(analysis.bestBackPrice().subtract(BigDecimal.ONE))
            : stake.negate();
        return new BacktestTrade(
            row.observedAt(),
            analysis.exchange(),
            analysis.marketId(),
            analysis.eventName(),
            analysis.marketName(),
            analysis.selectionId(),
            analysis.displayRunner(),
            BetSide.BACK,
            analysis.bestBackPrice(),
            stake,
            row.outcome(),
            profitLoss,
            row.competitionName(),
            analysis.score().confidenceLabel(),
            previousSnapshot.map(previous -> percentageDelta(previous.snapshot().bestBackPrice(), analysis.bestBackPrice())).orElse(null),
            BacktestRunnerType.fromSelectionId(row.selectionId())
        );
    }

    private String runnerKey(BacktestInputRow row) {
        return row.exchange() + "|" + row.marketId() + "|" + row.selectionId();
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
