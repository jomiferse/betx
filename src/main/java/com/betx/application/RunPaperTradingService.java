package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.PaperTradeRepository;
import com.betx.application.port.out.PaperTradeSettlementGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.PaperConfig;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs a read-only value-football-draw-only paper-trading scan over live market data. */
@Service
public class RunPaperTradingService {
    private static final int RECENT_SNAPSHOT_LIMIT = 10;
    private final BetxConfigRepository configRepository;
    private final Map<String, ExchangeMarketDataGateway> marketDataGateways;
    private final MarketSnapshotRepository snapshotRepository;
    private final PaperTradeRepository paperTradeRepository;
    private final Map<String, PaperTradeSettlementGateway> settlementGateways;
    private final Clock clock;
    private final EventMarketAnalyzer analyzer;

    @Autowired
    public RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        List<PaperTradeSettlementGateway> settlementGateways
    ) {
        this(configRepository, marketDataGateways, snapshotRepository, paperTradeRepository, settlementGateways, Clock.systemUTC());
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        List<PaperTradeSettlementGateway> settlementGateways,
        Clock clock
    ) {
        this.configRepository = configRepository;
        this.marketDataGateways = marketDataGateways.stream()
            .collect(Collectors.toMap(ExchangeMarketDataGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.snapshotRepository = snapshotRepository;
        this.paperTradeRepository = paperTradeRepository == null ? new NoopPaperTradeRepository() : paperTradeRepository;
        this.settlementGateways = (settlementGateways == null ? List.<PaperTradeSettlementGateway>of() : settlementGateways).stream()
            .collect(Collectors.toMap(PaperTradeSettlementGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.clock = clock;
        this.analyzer = new EventMarketAnalyzer();
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        Clock clock
    ) {
        this(configRepository, marketDataGateways, snapshotRepository, new NoopPaperTradeRepository(), List.of(), clock);
    }

    public PaperTradingResult run(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        return run(configPath, oddsSlippageRate, slippageModel, BigDecimal.ZERO);
    }

    public PaperConfig paperConfig(ConfigPath configPath) {
        return configRepository.load(configPath).paper();
    }

    public List<PaperTradingResult> runContinuous(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate,
        Duration pollInterval
    ) {
        return runContinuous(
            configPath,
            oddsSlippageRate,
            slippageModel,
            commissionRate,
            pollInterval,
            PaperTradingLoopControl.sleeping(),
            (cycle, result) -> {
            }
        );
    }

    public List<PaperTradingResult> runContinuous(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate,
        Duration pollInterval,
        PaperTradingLoopControl control
    ) {
        List<PaperTradingResult> results = new ArrayList<>();
        return runContinuous(configPath, oddsSlippageRate, slippageModel, commissionRate, pollInterval, control, (cycle, result) -> {
            results.add(result);
        });
    }

    public List<PaperTradingResult> runContinuous(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate,
        Duration pollInterval,
        PaperTradingLoopControl control,
        BiConsumer<Integer, PaperTradingResult> cycleReporter
    ) {
        PaperTradingLoopControl effectiveControl = control == null ? PaperTradingLoopControl.sleeping() : control;
        Duration effectivePollInterval = pollInterval == null ? Duration.ofSeconds(60) : pollInterval;
        BiConsumer<Integer, PaperTradingResult> effectiveReporter = cycleReporter == null ? (cycle, result) -> {
        } : cycleReporter;
        Thread owner = Thread.currentThread();
        Thread shutdownHook = new Thread(() -> {
            effectiveControl.requestStop();
            owner.interrupt();
        }, "betx-paper-trade-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        List<PaperTradingResult> results = new ArrayList<>();
        try {
            int cycle = 1;
            while (effectiveControl.shouldRunNextCycle()) {
                PaperTradingResult result = run(configPath, oddsSlippageRate, slippageModel, commissionRate);
                results.add(result);
                effectiveReporter.accept(cycle, result);
                cycle++;
                if (!effectiveControl.stopRequested()) {
                    effectiveControl.waitBeforeNextCycle(effectivePollInterval);
                }
            }
            return results;
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
        }
    }

    public PaperTradingResult run(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate
    ) {
        BetxConfig config = configRepository.load(configPath);
        Optional<StrategyConfig> strategyConfig = config.strategies().stream()
            .filter(strategy -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategy.name()) && strategy.enabled())
            .findFirst();
        if (strategyConfig.isEmpty() || config.enabledExchanges().isEmpty()) {
            return new PaperTradingResult(List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Instant observedAt = Instant.now(clock);
        List<String> failures = new ArrayList<>();
        int runnersAnalyzed = 0;
        int snapshotsSaved = 0;
        int recommendationsGenerated = 0;
        int duplicatesSkipped = 0;
        int executionFailures = 0;
        int missingClosingPrices = 0;
        int unsettledMarkets = 0;
        int settledTrades = 0;
        Set<String> marketsScanned = new HashSet<>();
        for (ExchangeConfig exchange : config.enabledExchanges().stream().sorted(Comparator.comparing(ExchangeConfig::name)).toList()) {
            ExchangeMarketDataGateway gateway = marketDataGateways.get(exchange.name());
            if (gateway == null) {
                failures.add("Exchange " + exchange.name() + " failed: no market data gateway configured");
                continue;
            }
            try {
                for (MarketSnapshot snapshot : gateway.listMarketData(exchange).snapshots()) {
                    marketsScanned.add(snapshot.exchange() + "|" + snapshot.marketId());
                    Optional<PaperTrade> existingPaperTrade = paperTradeRepository.findByMarketSelection(
                        config.storage().path(),
                        snapshot.exchange(),
                        snapshot.marketId(),
                        snapshot.selectionId()
                    );
                    if (existingPaperTrade.isPresent()) {
                        duplicatesSkipped++;
                        PaperTrade updated = advanceExistingTrade(
                            config,
                            observedAt,
                            snapshot,
                            existingPaperTrade.get(),
                            oddsSlippageRate,
                            slippageModel,
                            commissionRate,
                            config.paper().closingCaptureMinutesBeforeStart()
                        );
                        if (updated.status() == PaperTradeStatus.SETTLED
                            && existingPaperTrade.get().status() != PaperTradeStatus.SETTLED) {
                            settledTrades++;
                        }
                        if (updated.status() == PaperTradeStatus.EXECUTION_FAILED
                            && existingPaperTrade.get().status() == PaperTradeStatus.RECOMMENDED) {
                            executionFailures++;
                        }
                        if (updated.status() == PaperTradeStatus.EXECUTED) {
                            missingClosingPrices++;
                        }
                        if (updated.status() != PaperTradeStatus.SETTLED && updated.marketStartTime() != null
                            && !observedAt.isBefore(updated.marketStartTime())) {
                            unsettledMarkets++;
                        }
                        snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                        snapshotsSaved++;
                        continue;
                    }
                    List<ObservedMarketSnapshot> recent = snapshotRepository.findRecent(
                        config.storage().path(),
                        snapshot.exchange(),
                        snapshot.marketId(),
                        snapshot.selectionId(),
                        RECENT_SNAPSHOT_LIMIT
                    );
                    runnersAnalyzed++;
                    RunnerAnalysis analysis = analyzer.analyze(snapshot, recent, strategyConfig.get(), config.risk());
                    if (analysis.recommendation() == RecommendationType.BET
                        && BacktestRunnerType.fromSelectionId(snapshot.selectionId()) == BacktestRunnerType.DRAW) {
                        PaperTrade paperTrade = PaperTrade.recommended(snapshot, observedAt, config.risk().maxStake());
                        PaperTrade executed = executePaperTrade(observedAt, paperTrade, snapshot, oddsSlippageRate, slippageModel);
                        if (executed.status() == PaperTradeStatus.EXECUTION_FAILED) {
                            executionFailures++;
                        } else {
                            recommendationsGenerated++;
                        }
                        paperTradeRepository.upsert(config.storage().path(), executed);
                    }
                    snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                    snapshotsSaved++;
                }
            } catch (RuntimeException exc) {
                failures.add("Exchange " + exchange.name() + " failed: " + exc.getMessage());
            }
        }
        settledTrades += settlePersistedTrades(config, observedAt, commissionRate);
        List<BacktestPaperTrade> paperTrades = paperTradeRepository.listAll(config.storage().path()).stream()
            .sorted(Comparator.comparing(PaperTrade::recommendationTimestamp))
            .map(PaperTrade::toBacktestPaperTrade)
            .toList();
        return new PaperTradingResult(
            paperTrades,
            failures,
            runnersAnalyzed,
            snapshotsSaved,
            marketsScanned.size(),
            recommendationsGenerated,
            duplicatesSkipped,
            executionFailures,
            missingClosingPrices,
            unsettledMarkets,
            settledTrades
        );
    }

    private int settlePersistedTrades(BetxConfig config, Instant observedAt, BigDecimal commissionRate) {
        int settled = 0;
        for (PaperTrade trade : paperTradeRepository.listAll(config.storage().path())) {
            if ((trade.status() != PaperTradeStatus.CLOSED && trade.status() != PaperTradeStatus.EXECUTED) || !trade.matched()) {
                continue;
            }
            PaperTradeSettlementGateway settlementGateway = settlementGateways.get(trade.exchange());
            if (settlementGateway == null) {
                continue;
            }
            Optional<BacktestOutcome> outcome = settlementGateway.outcome(config, trade);
            if (outcome.isEmpty()) {
                continue;
            }
            paperTradeRepository.upsert(config.storage().path(), trade.withSettled(observedAt, outcome.get(), commissionRate));
            settled++;
        }
        return settled;
    }

    private PaperTrade executePaperTrade(
        Instant observedAt,
        PaperTrade paperTrade,
        MarketSnapshot snapshot,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        if (snapshot.bestBackPrice() == null
            || snapshot.bestBackPrice().compareTo(BigDecimal.ONE) <= 0
            || snapshot.liquidity().compareTo(paperTrade.stake()) < 0) {
            return paperTrade.withExecuted(observedAt, null, false);
        }
        BigDecimal executionOdds = (slippageModel == null ? BacktestSlippageModel.PROFIT_HAIRCUT : slippageModel)
            .adjustedOdds(snapshot.bestBackPrice(), oddsSlippageRate);
        return paperTrade.withExecuted(observedAt, executionOdds, true);
    }

    private PaperTrade advanceExistingTrade(
        BetxConfig config,
        Instant observedAt,
        MarketSnapshot snapshot,
        PaperTrade existing,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate,
        int closingCaptureMinutesBeforeStart
    ) {
        PaperTrade updated = existing;
        if (updated.status() == PaperTradeStatus.RECOMMENDED) {
            updated = executePaperTrade(observedAt, updated, snapshot, oddsSlippageRate, slippageModel);
            paperTradeRepository.upsert(config.storage().path(), updated);
        }
        if (updated.status() == PaperTradeStatus.EXECUTED
            && shouldCaptureClosingPrice(observedAt, snapshot, closingCaptureMinutesBeforeStart)) {
            updated = updated.withClosed(observedAt, snapshot.bestBackPrice());
            paperTradeRepository.upsert(config.storage().path(), updated);
        }
        if ((updated.status() == PaperTradeStatus.CLOSED || updated.status() == PaperTradeStatus.EXECUTED)
            && updated.matched()) {
            PaperTradeSettlementGateway settlementGateway = settlementGateways.get(updated.exchange());
            if (settlementGateway != null) {
                Optional<BacktestOutcome> outcome = settlementGateway.outcome(config, updated);
                if (outcome.isPresent()) {
                    updated = updated.withSettled(observedAt, outcome.get(), commissionRate);
                    paperTradeRepository.upsert(config.storage().path(), updated);
                }
            }
        }
        return updated;
    }

    private boolean shouldCaptureClosingPrice(Instant observedAt, MarketSnapshot snapshot, int closingCaptureMinutesBeforeStart) {
        if (snapshot.bestBackPrice() == null || snapshot.marketStartTime() == null) {
            return false;
        }
        Instant windowStart = snapshot.marketStartTime().minus(Duration.ofMinutes(closingCaptureMinutesBeforeStart));
        return !observedAt.isBefore(windowStart) && !observedAt.isAfter(snapshot.marketStartTime());
    }

    private static final class NoopPaperTradeRepository implements PaperTradeRepository {
        @Override
        public Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public void upsert(String databasePath, PaperTrade trade) {
        }

        @Override
        public List<PaperTrade> listAll(String databasePath) {
            return List.of();
        }
    }
}
