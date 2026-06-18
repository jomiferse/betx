package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.PaperSignalEvaluationRepository;
import com.betx.application.port.out.PaperTradeRepository;
import com.betx.application.port.out.PaperTradeSettlementGateway;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.PaperConfig;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final PaperSignalEvaluationRepository paperSignalEvaluationRepository;
    private final Map<String, PaperTradeSettlementGateway> settlementGateways;
    private final Clock clock;
    private final EventMarketAnalyzer analyzer;
    private final BetxEventLogger eventLogger;

    @Autowired
    public RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        PaperSignalEvaluationRepository paperSignalEvaluationRepository,
        List<PaperTradeSettlementGateway> settlementGateways,
        BetxEventLogger eventLogger
    ) {
        this(
            configRepository,
            marketDataGateways,
            snapshotRepository,
            paperTradeRepository,
            paperSignalEvaluationRepository,
            settlementGateways,
            Clock.systemUTC(),
            eventLogger
        );
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        List<PaperTradeSettlementGateway> settlementGateways,
        Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            snapshotRepository,
            paperTradeRepository,
            new NoopPaperSignalEvaluationRepository(),
            settlementGateways,
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        PaperSignalEvaluationRepository paperSignalEvaluationRepository,
        List<PaperTradeSettlementGateway> settlementGateways,
        Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            snapshotRepository,
            paperTradeRepository,
            paperSignalEvaluationRepository,
            settlementGateways,
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        PaperTradeRepository paperTradeRepository,
        PaperSignalEvaluationRepository paperSignalEvaluationRepository,
        List<PaperTradeSettlementGateway> settlementGateways,
        Clock clock,
        BetxEventLogger eventLogger
    ) {
        this.configRepository = configRepository;
        this.marketDataGateways = marketDataGateways.stream()
            .collect(Collectors.toMap(ExchangeMarketDataGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.snapshotRepository = snapshotRepository;
        this.paperTradeRepository = paperTradeRepository == null ? new NoopPaperTradeRepository() : paperTradeRepository;
        this.paperSignalEvaluationRepository = paperSignalEvaluationRepository == null
            ? new NoopPaperSignalEvaluationRepository()
            : paperSignalEvaluationRepository;
        this.settlementGateways = (settlementGateways == null ? List.<PaperTradeSettlementGateway>of() : settlementGateways).stream()
            .collect(Collectors.toMap(PaperTradeSettlementGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.clock = clock;
        this.analyzer = new EventMarketAnalyzer();
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), clock) : eventLogger;
    }

    RunPaperTradingService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        MarketSnapshotRepository snapshotRepository,
        Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            snapshotRepository,
            new NoopPaperTradeRepository(),
            new NoopPaperSignalEvaluationRepository(),
            List.of(),
            clock
        );
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
                PaperTradingResult result;
                try {
                    result = run(configPath, oddsSlippageRate, slippageModel, commissionRate);
                } catch (RuntimeException exc) {
                    result = failedCycleResult(exc);
                }
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

    private PaperTradingResult failedCycleResult(RuntimeException exc) {
        String message = exc.getMessage() == null || exc.getMessage().isBlank()
            ? exc.getClass().getSimpleName()
            : exc.getMessage();
        return new PaperTradingResult(
            List.of(),
            List.of("Paper trading cycle failed: " + message),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
    }

    public PaperTradingResult run(
        ConfigPath configPath,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BigDecimal commissionRate
    ) {
        Instant cycleStarted = Instant.now(clock);
        String cycleId = "paper-cycle-" + cycleStarted.toString();
        long startedNanos = System.nanoTime();
        eventLogger.info(BetxEventCategory.OPERATIONAL, "cycle.started")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode("paper")
            .result("started")
            .emit();
        BetxConfig config = configRepository.load(configPath);
        eventLogger.configure(config.app());
        Optional<StrategyConfig> strategyConfig = config.strategies().stream()
            .filter(strategy -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategy.name()) && strategy.enabled())
            .findFirst();
        if (strategyConfig.isEmpty() || config.enabledExchanges().isEmpty()) {
            return new PaperTradingResult(List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Instant observedAt = Instant.now(clock);
        List<String> failures = new ArrayList<>();
        HistoryDiagnosticAccumulator diagnostics = new HistoryDiagnosticAccumulator();
        int runnersAnalyzed = 0;
        int snapshotsSaved = 0;
        int recommendationsGenerated = 0;
        int duplicatesSkipped = 0;
        int executionFailures = 0;
        int missingClosingPrices = 0;
        int unsettledMarkets = 0;
        int settledTrades = 0;
        Set<String> marketsScanned = new HashSet<>();
        List<PaperSignalEvaluation> signalEvaluations = new ArrayList<>();
        for (ExchangeConfig exchange : config.enabledExchanges().stream().sorted(Comparator.comparing(ExchangeConfig::name)).toList()) {
            ExchangeMarketDataGateway gateway = marketDataGateways.get(exchange.name());
            if (gateway == null) {
                failures.add("Exchange " + exchange.name() + " failed: no market data gateway configured");
                dependencyError(cycleId, exchange.name(), "market_data_gateway", new IllegalStateException("No market data gateway configured."));
                continue;
            }
            try {
                eventLogger.info(BetxEventCategory.OPERATIONAL, "market.scan.started")
                    .correlationId(cycleId)
                    .cycleId(cycleId)
                    .exchange(exchange.name())
                    .executionMode("paper")
                    .result("started")
                    .emit();
                List<MarketSnapshot> snapshots = gateway.listMarketData(exchange).snapshots();
                diagnostics.recordMarketInvariants(snapshots);
                for (MarketSnapshot snapshot : snapshots) {
                    marketsScanned.add(snapshot.exchange() + "|" + snapshot.marketId());
                    if (analyzer.isTestMarket(snapshot)) {
                        diagnostics.recordAnalyzerOutcome(PaperTradeAnalyzerRejectionReason.TEST_MARKET);
                        continue;
                    }
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
                            logPaperTrade("paper_trade.settled", "settled", updated, cycleId)
                                .field("result", updated.result())
                                .field("netPnl", updated.netPnl())
                                .emit();
                        }
                        if (updated.status() == PaperTradeStatus.EXECUTION_FAILED
                            && existingPaperTrade.get().status() == PaperTradeStatus.RECOMMENDED) {
                            executionFailures++;
                            logPaperTrade("paper_trade.execution_failed", "failed", updated, cycleId).emit();
                        }
                        if (updated.status() == PaperTradeStatus.EXECUTED) {
                            missingClosingPrices++;
                        }
                        if (updated.status() == PaperTradeStatus.CLOSED
                            && existingPaperTrade.get().status() != PaperTradeStatus.CLOSED) {
                            logPaperTrade("paper_trade.closed", "closed", updated, cycleId)
                                .field("closingOdds", updated.closingOdds())
                                .emit();
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
                    diagnostics.recordHistory(snapshot, recent);
                    diagnostics.recordRunnerClassification(snapshot);
                    runnersAnalyzed++;
                    RunnerAnalysis analysis = analyzer.analyze(snapshot, recent, strategyConfig.get(), config.risk());
                    PaperTradeAnalyzerRejectionReason analyzerOutcome;
                    if (analysis.recommendation() == RecommendationType.BET
                        && runnerType(snapshot) == RunnerType.DRAW) {
                        analyzerOutcome = PaperTradeAnalyzerRejectionReason.ACCEPTED;
                        diagnostics.recordAnalyzerOutcome(analyzerOutcome);
                        PaperTrade paperTrade = PaperTrade.recommended(snapshot, observedAt, config.risk().maxStake());
                        PaperTrade executed = executePaperTrade(observedAt, paperTrade, snapshot, oddsSlippageRate, slippageModel);
                        logPaperTrade("paper_trade.recommended", "recommended", paperTrade, cycleId)
                            .field("requestedOdds", paperTrade.requestedOdds())
                            .emit();
                        if (executed.status() == PaperTradeStatus.EXECUTION_FAILED) {
                            executionFailures++;
                            logPaperTrade("paper_trade.execution_failed", "failed", executed, cycleId)
                                .field("reason", "insufficient_liquidity_or_invalid_odds")
                                .emit();
                        } else {
                            recommendationsGenerated++;
                            logPaperTrade("paper_trade.executed", "executed", executed, cycleId)
                                .field("executionOdds", executed.executionOdds())
                                .emit();
                        }
                        paperTradeRepository.upsert(config.storage().path(), executed);
                    } else {
                        analyzerOutcome = classifyAnalyzerOutcome(snapshot, recent, analysis);
                        diagnostics.recordAnalyzerOutcome(analyzerOutcome);
                        eventLogger.info(BetxEventCategory.ANALYTICS, "signal.rejected")
                            .correlationId(signalCorrelationId(observedAt, snapshot.exchange(), snapshot.marketId(), snapshot.selectionId()))
                            .cycleId(cycleId)
                            .exchange(snapshot.exchange())
                            .marketId(snapshot.marketId())
                            .selectionId(snapshot.selectionId())
                            .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
                            .executionMode("paper")
                            .result("rejected")
                            .field("reason", analyzerOutcome)
                            .field("odds", snapshot.bestBackPrice())
                            .field("liquidity", snapshot.liquidity())
                            .emit();
                    }
                    PaperSignalEvaluation evaluation = toSignalEvaluation(observedAt, snapshot, recent, analysis, analyzerOutcome);
                    if (shouldPersistSignalEvaluation(evaluation)) {
                        signalEvaluations.add(evaluation);
                        safeSaveSignalEvaluation(config, evaluation, failures);
                    }
                    snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                    snapshotsSaved++;
                }
                eventLogger.info(BetxEventCategory.OPERATIONAL, "market.scan.completed")
                    .correlationId(cycleId)
                    .cycleId(cycleId)
                    .exchange(exchange.name())
                    .executionMode("paper")
                    .result("completed")
                    .field("snapshots", snapshots.size())
                    .emit();
            } catch (RuntimeException exc) {
                failures.add("Exchange " + exchange.name() + " failed: " + exc.getMessage());
                dependencyError(cycleId, exchange.name(), "paper_market_scan", exc);
            }
        }
        settledTrades += settlePersistedTrades(config, observedAt, commissionRate, cycleId);
        List<BacktestPaperTrade> paperTrades = paperTradeRepository.listAll(config.storage().path()).stream()
            .sorted(Comparator.comparing(PaperTrade::recommendationTimestamp))
            .map(PaperTrade::toBacktestPaperTrade)
            .toList();
        PaperTradingResult result = new PaperTradingResult(
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
            settledTrades,
            diagnostics.toDiagnostics(),
            signalEvaluations
        );
        eventLogger.info(BetxEventCategory.OPERATIONAL, "cycle.completed")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode("paper")
            .result(failures.isEmpty() ? "completed" : "completed_with_failures")
            .field("durationMs", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos))
            .field("marketsScanned", result.marketsScanned())
            .field("runnersAnalyzed", result.runnersAnalyzed())
            .field("recommendationsGenerated", result.recommendationsGenerated())
            .field("duplicatesSkipped", result.duplicatesSkipped())
            .field("executionFailures", result.executionFailures())
            .field("unsettledMarkets", result.unsettledMarkets())
            .field("settledTrades", result.settledTrades())
            .field("failures", result.failures().size())
            .emit();
        eventLogger.info(BetxEventCategory.ANALYTICS, "cycle.metrics.recorded")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode("paper")
            .result("recorded")
            .field("snapshotsSaved", result.snapshotsSaved())
            .field("paperEvaluations", result.paperSignalEvaluations().size())
            .emit();
        return result;
    }

    private PaperSignalEvaluation toSignalEvaluation(
        Instant observedAt,
        MarketSnapshot snapshot,
        List<ObservedMarketSnapshot> recent,
        RunnerAnalysis analysis,
        PaperTradeAnalyzerRejectionReason analyzerReason
    ) {
        MarketSnapshot previous = recent == null || recent.isEmpty() ? null : recent.getFirst().snapshot();
        return new PaperSignalEvaluation(
            observedAt,
            snapshot.exchange(),
            snapshot.marketId(),
            snapshot.marketName(),
            snapshot.eventName(),
            snapshot.competitionName(),
            snapshot.marketStartTime(),
            snapshot.selectionId(),
            snapshot.runnerName(),
            runnerType(snapshot),
            analysis.recommendation(),
            analysis.score().value(),
            analysis.score().confidenceLabel(),
            analysis.reason(),
            snapshot.bestBackPrice(),
            snapshot.bestLayPrice(),
            snapshot.spread(),
            snapshot.liquidity(),
            previous == null ? null : percentageDelta(previous.bestBackPrice(), snapshot.bestBackPrice()),
            previous == null ? null : percentageDelta(previous.bestLayPrice(), snapshot.bestLayPrice()),
            previous == null ? null : percentageDelta(previous.liquidity(), snapshot.liquidity()),
            analyzerReason
        );
    }

    private void safeSaveSignalEvaluation(
        BetxConfig config,
        PaperSignalEvaluation evaluation,
        List<String> failures
    ) {
        try {
            paperSignalEvaluationRepository.save(config.storage().path(), evaluation);
        } catch (RuntimeException exc) {
            failures.add("Paper signal evaluation history failed: " + exc.getMessage());
        }
    }

    private boolean shouldPersistSignalEvaluation(PaperSignalEvaluation evaluation) {
        if (evaluation.analyzerReason() == PaperTradeAnalyzerRejectionReason.ACCEPTED) {
            return true;
        }
        if (evaluation.analyzerReason() == PaperTradeAnalyzerRejectionReason.NOT_DRAW) {
            return false;
        }
        return evaluation.runnerType() == RunnerType.DRAW
            || evaluation.recommendation() == RecommendationType.BET
            || evaluation.recommendation() == RecommendationType.WATCH;
    }

    private PaperTradeAnalyzerRejectionReason classifyAnalyzerOutcome(
        MarketSnapshot snapshot,
        List<ObservedMarketSnapshot> recent,
        RunnerAnalysis analysis
    ) {
        if (runnerType(snapshot) != RunnerType.DRAW) {
            return PaperTradeAnalyzerRejectionReason.NOT_DRAW;
        }
        if (recent == null || recent.isEmpty()) {
            return PaperTradeAnalyzerRejectionReason.INSUFFICIENT_HISTORY;
        }
        MarketSnapshot previous = recent.getFirst().snapshot();
        if (samePrice(previous.bestBackPrice(), snapshot.bestBackPrice())) {
            return PaperTradeAnalyzerRejectionReason.ODDS_UNCHANGED;
        }
        String reason = analysis.reason();
        if (reason.contains("liquidity_below_minimum")) {
            return PaperTradeAnalyzerRejectionReason.LIQUIDITY_BELOW_THRESHOLD;
        }
        if (reason.contains("spread_above_threshold")) {
            return PaperTradeAnalyzerRejectionReason.SPREAD_ABOVE_THRESHOLD;
        }
        if (reason.contains("odds_out_of_range") || reason.contains("missing_back_or_lay_price")) {
            return PaperTradeAnalyzerRejectionReason.ODDS_OUT_OF_RANGE;
        }
        if (reason.contains("score_below_threshold")) {
            return PaperTradeAnalyzerRejectionReason.CONFIDENCE_BELOW_THRESHOLD;
        }
        if (reason.contains("draw_runner_not_supported")) {
            return PaperTradeAnalyzerRejectionReason.MOVEMENT_BELOW_THRESHOLD;
        }
        return PaperTradeAnalyzerRejectionReason.CONFIDENCE_BELOW_THRESHOLD;
    }

    private RunnerType runnerType(MarketSnapshot snapshot) {
        RunnerType type = snapshot.runnerType();
        if (type != null && type != RunnerType.UNKNOWN) {
            return type;
        }
        return switch (BacktestRunnerType.fromSelectionId(snapshot.selectionId())) {
            case HOME -> RunnerType.HOME;
            case DRAW -> RunnerType.DRAW;
            case AWAY -> RunnerType.AWAY;
            case UNKNOWN -> RunnerType.UNKNOWN;
        };
    }

    private boolean samePrice(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
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

    private int settlePersistedTrades(BetxConfig config, Instant observedAt, BigDecimal commissionRate, String cycleId) {
        int settled = 0;
        for (PaperTrade trade : paperTradeRepository.listAll(config.storage().path())) {
            if ((trade.status() != PaperTradeStatus.CLOSED && trade.status() != PaperTradeStatus.EXECUTED) || !trade.matched()) {
                continue;
            }
            PaperTradeSettlementGateway settlementGateway = settlementGateways.get(trade.exchange());
            if (settlementGateway == null) {
                continue;
            }
            Optional<BacktestOutcome> outcome = safeSettlementOutcome(config, settlementGateway, trade);
            if (outcome.isEmpty()) {
                continue;
            }
            PaperTrade settledTrade = trade.withSettled(observedAt, outcome.get(), commissionRate);
            paperTradeRepository.upsert(config.storage().path(), settledTrade);
            logPaperTrade("paper_trade.settled", "settled", settledTrade, cycleId)
                .field("result", settledTrade.result())
                .field("netPnl", settledTrade.netPnl())
                .emit();
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
                Optional<BacktestOutcome> outcome = safeSettlementOutcome(config, settlementGateway, updated);
                if (outcome.isPresent()) {
                    updated = updated.withSettled(observedAt, outcome.get(), commissionRate);
                    paperTradeRepository.upsert(config.storage().path(), updated);
                }
            }
        }
        return updated;
    }

    private Optional<BacktestOutcome> safeSettlementOutcome(
        BetxConfig config,
        PaperTradeSettlementGateway settlementGateway,
        PaperTrade trade
    ) {
        try {
            return settlementGateway.outcome(config, trade);
        } catch (RuntimeException exc) {
            dependencyError(
                "paper-cycle-" + Instant.now(clock),
                trade.exchange(),
                "paper_settlement",
                exc
            );
            return Optional.empty();
        }
    }

    private boolean shouldCaptureClosingPrice(Instant observedAt, MarketSnapshot snapshot, int closingCaptureMinutesBeforeStart) {
        if (snapshot.bestBackPrice() == null || snapshot.marketStartTime() == null) {
            return false;
        }
        Instant windowStart = snapshot.marketStartTime().minus(Duration.ofMinutes(closingCaptureMinutesBeforeStart));
        return !observedAt.isBefore(windowStart) && !observedAt.isAfter(snapshot.marketStartTime());
    }

    private BetxEventLogger.EventBuilder logPaperTrade(String event, String result, PaperTrade trade, String cycleId) {
        return eventLogger.info(BetxEventCategory.ANALYTICS, event)
            .correlationId("paper-" + trade.id())
            .cycleId(cycleId)
            .exchange(trade.exchange())
            .marketId(trade.marketId())
            .selectionId(trade.selectionId())
            .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
            .executionMode("paper")
            .result(result)
            .field("paperTradeId", trade.id())
            .field("status", trade.status())
            .field("runner", trade.runnerName())
            .field("stake", trade.stake())
            .field("requestedOdds", trade.requestedOdds())
            .field("matched", trade.matched());
    }

    private String signalCorrelationId(Instant observedAt, String exchange, String marketId, long selectionId) {
        return "sig-" + exchange + "-" + marketId + "-" + selectionId + "-" + observedAt;
    }

    private void dependencyError(String cycleId, String dependency, String action, RuntimeException exc) {
        eventLogger.error(BetxEventCategory.ERROR, "dependency.error")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .result("failed")
            .field("dependency", dependency)
            .field("action", action)
            .field("errorType", exc.getClass().getSimpleName())
            .field("message", safeMessage(exc))
            .emit();
    }

    private String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        return message == null || message.isBlank() ? exc.getClass().getSimpleName() : message;
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

    private static final class NoopPaperSignalEvaluationRepository implements PaperSignalEvaluationRepository {
        @Override
        public void save(String databasePath, PaperSignalEvaluation evaluation) {
        }

        @Override
        public List<PaperSignalEvaluation> listLatest(String databasePath, int limit) {
            return List.of();
        }
    }

    private static final class HistoryDiagnosticAccumulator {
        private int previousSnapshotsLoaded;
        private int runnersWithoutPreviousSnapshot;
        private int runnersWithPreviousSnapshot;
        private int runnersWithSufficientHistory;
        private int runnersWithChangedOdds;
        private int runnersWithUnchangedOdds;
        private Instant oldestPreviousSnapshot;
        private Instant newestPreviousSnapshot;
        private final Set<String> stableMarketKeys = new HashSet<>();
        private final Set<String> stableSelectionKeys = new HashSet<>();
        private final List<PaperTradeRunnerClassificationDiagnostic> runnerClassificationSample = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final EnumMap<PaperTradeAnalyzerRejectionReason, Integer> analyzerRejectionCounts =
            new EnumMap<>(PaperTradeAnalyzerRejectionReason.class);
        private static final int MAX_CLASSIFICATION_SAMPLE = 12;

        private void recordHistory(MarketSnapshot current, List<ObservedMarketSnapshot> recent) {
            List<ObservedMarketSnapshot> safeRecent = recent == null ? List.of() : recent;
            previousSnapshotsLoaded += safeRecent.size();
            if (safeRecent.isEmpty()) {
                runnersWithoutPreviousSnapshot++;
                return;
            }
            runnersWithPreviousSnapshot++;
            runnersWithSufficientHistory++;
            stableMarketKeys.add(current.exchange() + "|" + current.marketId());
            stableSelectionKeys.add(current.exchange() + "|" + current.marketId() + "|" + current.selectionId());
            ObservedMarketSnapshot previous = safeRecent.getFirst();
            if (samePrice(previous.snapshot().bestBackPrice(), current.bestBackPrice())) {
                runnersWithUnchangedOdds++;
            } else {
                runnersWithChangedOdds++;
            }
            safeRecent.stream().map(ObservedMarketSnapshot::observedAt).forEach(this::recordPreviousObservedAt);
        }

        private void recordAnalyzerOutcome(PaperTradeAnalyzerRejectionReason reason) {
            analyzerRejectionCounts.merge(reason, 1, Integer::sum);
        }

        private void recordRunnerClassification(MarketSnapshot snapshot) {
            if (runnerClassificationSample.size() >= MAX_CLASSIFICATION_SAMPLE) {
                return;
            }
            RunnerType type = snapshot.runnerType() == null ? RunnerType.UNKNOWN : snapshot.runnerType();
            runnerClassificationSample.add(new PaperTradeRunnerClassificationDiagnostic(
                snapshot.marketId(),
                snapshot.marketName(),
                snapshot.selectionId(),
                snapshot.runnerName(),
                normalizeRunnerName(snapshot.runnerName()),
                type,
                type == RunnerType.DRAW
            ));
        }

        private void recordMarketInvariants(List<MarketSnapshot> snapshots) {
            Map<String, List<MarketSnapshot>> snapshotsByMarket = (snapshots == null ? List.<MarketSnapshot>of() : snapshots).stream()
                .filter(snapshot -> snapshot.marketName() != null && "match odds".equalsIgnoreCase(snapshot.marketName().strip()))
                .collect(Collectors.groupingBy(
                    snapshot -> snapshot.exchange() + "|" + snapshot.marketId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            for (Map.Entry<String, List<MarketSnapshot>> entry : snapshotsByMarket.entrySet()) {
                List<MarketSnapshot> marketSnapshots = entry.getValue();
                if (marketSnapshots.size() != 3) {
                    continue;
                }
                long drawRunners = marketSnapshots.stream()
                    .filter(snapshot -> snapshot.runnerType() == RunnerType.DRAW)
                    .count();
                if (drawRunners == 0) {
                    String runnerNames = marketSnapshots.stream()
                        .map(snapshot -> snapshot.runnerName() == null ? String.valueOf(snapshot.selectionId()) : snapshot.runnerName())
                        .collect(Collectors.joining(", "));
                    warnings.add("PAPER_WARNING | complete Match Odds market has zero DRAW runners"
                        + " | marketId=" + marketSnapshots.getFirst().marketId()
                        + " | runnerNames=" + runnerNames);
                }
            }
        }

        private PaperTradeHistoryDiagnostics toDiagnostics() {
            return new PaperTradeHistoryDiagnostics(
                previousSnapshotsLoaded,
                runnersWithoutPreviousSnapshot,
                runnersWithPreviousSnapshot,
                runnersWithSufficientHistory,
                runnersWithChangedOdds,
                runnersWithUnchangedOdds,
                oldestPreviousSnapshot,
                newestPreviousSnapshot,
                stableMarketKeys.size(),
                stableSelectionKeys.size(),
                runnerClassificationSample,
                warnings,
                analyzerRejectionCounts
            );
        }

        private void recordPreviousObservedAt(Instant observedAt) {
            if (observedAt == null) {
                return;
            }
            if (oldestPreviousSnapshot == null || observedAt.isBefore(oldestPreviousSnapshot)) {
                oldestPreviousSnapshot = observedAt;
            }
            if (newestPreviousSnapshot == null || observedAt.isAfter(newestPreviousSnapshot)) {
                newestPreviousSnapshot = observedAt;
            }
        }

        private boolean samePrice(BigDecimal left, BigDecimal right) {
            if (left == null || right == null) {
                return left == right;
            }
            return left.compareTo(right) == 0;
        }

        private String normalizeRunnerName(String runnerName) {
            if (runnerName == null || runnerName.isBlank()) {
                return null;
            }
            return runnerName.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }
}
