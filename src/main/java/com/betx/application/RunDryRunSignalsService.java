package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetRecommendationRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.ExternalMatchIntelligenceGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.SignalHistoryRepository;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.IntelligenceAutoBettingPolicy;
import com.betx.domain.config.IntelligenceConfig;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs one read-only multi-exchange cycle and emits strategy signals. */
@Service
public class RunDryRunSignalsService {
    private static final int RECENT_SNAPSHOT_LIMIT = 10;

    private final BetxConfigRepository configRepository;
    private final Map<String, ExchangeMarketDataGateway> marketDataGateways;
    private final TelegramConnectionService telegramService;
    private final BetExecutionGateway executionGateway;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketSnapshotChangeDetector changeDetector;
    private final ExternalMatchIntelligenceGateway intelligenceGateway;
    private final SignalHistoryRepository signalHistoryRepository;
    private final BetRecommendationRepository betRecommendationRepository;
    private final BetxEventLogger eventLogger;
    private final Clock clock;
    private final EventMarketAnalyzer analyzer;
    private final TelegramBetAlertFormatter telegramBetAlertFormatter;
    private final TelegramBetAlertPolicy telegramBetAlertPolicy;
    private final ThreadLocal<Consumer<String>> output = ThreadLocal.withInitial(() -> ignored -> {
    });

    @Autowired
    public RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector,
        ExternalMatchIntelligenceGateway intelligenceGateway,
        SignalHistoryRepository signalHistoryRepository,
        BetRecommendationRepository betRecommendationRepository,
        BetxEventLogger eventLogger
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            snapshotRepository,
            changeDetector,
            intelligenceGateway,
            signalHistoryRepository,
            betRecommendationRepository,
            Clock.systemUTC(),
            eventLogger
        );
    }

    RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector,
            Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            snapshotRepository,
            changeDetector,
            new NoopExternalMatchIntelligenceGateway(),
            new NoopSignalHistoryRepository(),
            new NoopBetRecommendationRepository(),
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector,
            ExternalMatchIntelligenceGateway intelligenceGateway,
            Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            snapshotRepository,
            changeDetector,
            intelligenceGateway,
            new NoopSignalHistoryRepository(),
            new NoopBetRecommendationRepository(),
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector,
        ExternalMatchIntelligenceGateway intelligenceGateway,
        SignalHistoryRepository signalHistoryRepository,
        Clock clock
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            snapshotRepository,
            changeDetector,
            intelligenceGateway,
            signalHistoryRepository,
            new NoopBetRecommendationRepository(),
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector,
        ExternalMatchIntelligenceGateway intelligenceGateway,
        SignalHistoryRepository signalHistoryRepository,
        BetRecommendationRepository betRecommendationRepository,
        Clock clock,
        BetxEventLogger eventLogger
    ) {
        this.configRepository = configRepository;
        this.marketDataGateways = marketDataGateways.stream()
            .collect(Collectors.toMap(ExchangeMarketDataGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.telegramService = telegramService;
        this.executionGateway = executionGateway;
        this.snapshotRepository = snapshotRepository;
        this.changeDetector = changeDetector;
        this.intelligenceGateway = intelligenceGateway == null ? new NoopExternalMatchIntelligenceGateway() : intelligenceGateway;
        this.signalHistoryRepository = signalHistoryRepository == null ? new NoopSignalHistoryRepository() : signalHistoryRepository;
        this.betRecommendationRepository = betRecommendationRepository == null
            ? new NoopBetRecommendationRepository()
            : betRecommendationRepository;
        this.clock = clock;
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), clock) : eventLogger;
        this.analyzer = new EventMarketAnalyzer();
        this.telegramBetAlertFormatter = new TelegramBetAlertFormatter();
        this.telegramBetAlertPolicy = new TelegramBetAlertPolicy();
    }

    public RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            new NoopMarketSnapshotRepository(),
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new NoopSignalHistoryRepository(),
            new NoopBetRecommendationRepository(),
            Clock.systemUTC(),
            new BetxEventLogger(StructuredEventSink.noop())
        );
    }

    public RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        ExternalMatchIntelligenceGateway intelligenceGateway
    ) {
        this(
            configRepository,
            marketDataGateways,
            telegramService,
            executionGateway,
            new NoopMarketSnapshotRepository(),
            new MarketSnapshotChangeDetector(),
            intelligenceGateway,
            new NoopSignalHistoryRepository(),
            new NoopBetRecommendationRepository(),
            Clock.systemUTC(),
            new BetxEventLogger(StructuredEventSink.noop())
        );
    }

    /** Runs one signal cycle across every enabled exchange. */
    public DryRunSignalsResult run(ConfigPath configPath) {
        return run(configPath, true);
    }

    public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts) {
        return run(configPath, sendTelegramAlerts, true);
    }

    public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
        return run(configPath, sendTelegramAlerts, logSuppressedTelegramAlerts, ignored -> {
        });
    }

    public DryRunSignalsResult run(
        ConfigPath configPath,
        boolean sendTelegramAlerts,
        boolean logSuppressedTelegramAlerts,
        Consumer<String> outputConsumer
    ) {
        output.set(outputConsumer == null ? ignored -> {
        } : outputConsumer);
        try {
            return runInternal(configPath, sendTelegramAlerts, logSuppressedTelegramAlerts);
        } finally {
            output.remove();
        }
    }

    private DryRunSignalsResult runInternal(ConfigPath configPath, boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
        Instant cycleStarted = Instant.now(clock);
        String cycleId = cycleId(cycleStarted);
        long startedNanos = System.nanoTime();
        eventLogger.info(BetxEventCategory.OPERATIONAL, "cycle.started")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode(executionMode(sendTelegramAlerts, logSuppressedTelegramAlerts))
            .result("started")
            .emit();
        BetxConfig config = configRepository.load(configPath);
        eventLogger.configure(config.app());
        eventLogger.info(BetxEventCategory.OPERATIONAL, "config.loaded")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .result("loaded")
            .field("configPath", configPath == null ? "betx.yml" : configPath.value().toString())
            .field("structuredLogsEnabled", config.app().structuredLogs().enabled())
            .emit();
        cleanupExpiredMarketSnapshots(config);

        Optional<StrategyConfig> strategyConfig = valueFootballStrategy(config);
        if (strategyConfig.isEmpty() || !strategyConfig.get().enabled()) {
            return new DryRunSignalsResult(List.of(), List.of(), false);
        }

        List<ExchangeConfig> enabledExchanges = config.enabledExchanges();
        if (enabledExchanges.isEmpty()) {
            return new DryRunSignalsResult(List.of(), List.of(), true);
        }

        List<BetSignal> signals = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        List<MarketSnapshotChange> changes = new ArrayList<>();
        List<RunnerAnalysis> runnerAnalyses = new ArrayList<>();
        List<MatchIntelligenceAssessment> intelligenceAssessments = new ArrayList<>();
        List<SignalHistoryEntry> signalHistoryEntries = new ArrayList<>();
        List<TelegramBetAlertCandidate> telegramAlerts = new ArrayList<>();
        Set<String> marketsRead = new java.util.LinkedHashSet<>();
        Set<String> ignoredMarkets = new java.util.LinkedHashSet<>();
        int eventsRead = 0;
        int ignoredEvents = 0;
        int snapshotsSaved = 0;
        int comparisonsCalculated = 0;
        Instant observedAt = Instant.now(clock);
        for (ExchangeConfig exchange : enabledExchanges.stream().sorted(Comparator.comparing(ExchangeConfig::name)).toList()) {
            ExchangeMarketDataGateway gateway = marketDataGateways.get(exchange.name());
            if (gateway == null) {
                failures.add("Exchange " + exchange.name() + " failed: no market data gateway configured");
                dependencyError(cycleId, exchange.name(), "market_data_gateway", "IllegalStateException", "No market data gateway configured.");
                continue;
            }
            eventLogger.info(BetxEventCategory.OPERATIONAL, "market.scan.started")
                .correlationId(cycleId)
                .cycleId(cycleId)
                .exchange(exchange.name())
                .result("started")
                .emit();
            try {
                ExchangeMarketDataResult marketDataResult = gateway.listMarketData(exchange);
                eventsRead += marketDataResult.eventsRead();
                ignoredEvents += marketDataResult.ignoredEvents();
                List<MarketSnapshot> snapshots = marketDataResult.snapshots();
                for (MarketSnapshot snapshot : snapshots) {
                    marketsRead.add(snapshot.exchange() + "|" + snapshot.marketId());
                    if (analyzer.isTestMarket(snapshot)) {
                        ignoredMarkets.add(snapshot.exchange() + "|" + snapshot.marketId());
                        continue;
                    }
                    Optional<ObservedMarketSnapshot> previous = snapshotRepository.findLatest(
                        config.storage().path(),
                        snapshot.exchange(),
                        snapshot.marketId(),
                        snapshot.selectionId()
                    );
                    List<ObservedMarketSnapshot> recent = snapshotRepository.findRecent(
                        config.storage().path(),
                        snapshot.exchange(),
                        snapshot.marketId(),
                        snapshot.selectionId(),
                        RECENT_SNAPSHOT_LIMIT
                    );
                    Optional<MarketSnapshotChange> currentChange = Optional.empty();
                    if (previous.isPresent()) {
                        comparisonsCalculated++;
                        currentChange = changeDetector.compare(previous.get().snapshot(), snapshot);
                        currentChange.ifPresent(changes::add);
                    }
                    String evaluationId = UUID.randomUUID().toString();
                    RunnerAnalysis analysis = analyzer.analyze(
                        snapshot,
                        recent,
                        strategyConfig.get(),
                        config.risk()
                    ).withEvaluationId(evaluationId);
                    runnerAnalyses.add(analysis);
                    logSignalDecision(cycleId, observedAt, analysis);
                    Optional<MatchIntelligenceAssessment> assessment = Optional.empty();
                    if (analysis.recommendation() == RecommendationType.BET) {
                        shadowPersistRecommendation(config, cycleId, observedAt, analysis);
                        eventLogger.info(BetxEventCategory.ANALYTICS, "intelligence.requested")
                            .correlationId(signalCorrelationId(observedAt, analysis.exchange(), analysis.marketId(), analysis.selectionId()))
                            .cycleId(cycleId)
                            .exchange(analysis.exchange())
                            .marketId(analysis.marketId())
                            .selectionId(analysis.selectionId())
                            .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
                            .executionMode(executionMode(sendTelegramAlerts, logSuppressedTelegramAlerts))
                            .result(config.intelligence().enabled() ? "requested" : "disabled")
                            .emit();
                        assessment = assessIntelligence(config, analysis);
                        assessment.ifPresent(intelligenceAssessments::add);
                        assessment.ifPresent(this::auditIntelligenceAssessment);
                    }
                    saveSignalHistory(config, observedAt, analysis, currentChange, assessment).ifPresent(signalHistoryEntries::add);
                    if (analysis.recommendation() == RecommendationType.BET) {
                        if (blocksSignalForAutoBetting(config, analysis.exchange(), assessment)) {
                            eventLogger.warn(BetxEventCategory.ANALYTICS, "signal.blocked")
                                .correlationId(signalCorrelationId(observedAt, analysis.exchange(), analysis.marketId(), analysis.selectionId()))
                                .cycleId(cycleId)
                                .exchange(analysis.exchange())
                                .marketId(analysis.marketId())
                                .selectionId(analysis.selectionId())
                                .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
                                .executionMode(executionMode(sendTelegramAlerts, logSuppressedTelegramAlerts))
                                .result("blocked")
                                .field("reason", "intelligence")
                                .field("decision", assessment.map(value -> value.decision().name()).orElse("missing"))
                                .emit();
                            auditIntelligenceBlocked(config, analysis, assessment.orElse(null));
                            snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                            snapshotsSaved++;
                            continue;
                        }
                        auditIntelligenceAllowedByPolicy(config, analysis, assessment);
                        signals.add(toSignal(analysis, config));
                        TelegramBetAlertCandidate.tryFrom(analysis, previous.map(ObservedMarketSnapshot::snapshot))
                            .ifPresent(telegramAlerts::add);
                    }
                    snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                    snapshotsSaved++;
                }
                eventLogger.info(BetxEventCategory.OPERATIONAL, "market.scan.completed")
                    .correlationId(cycleId)
                    .cycleId(cycleId)
                    .exchange(exchange.name())
                    .result("completed")
                    .field("eventsRead", marketDataResult.eventsRead())
                    .field("ignoredEvents", marketDataResult.ignoredEvents())
                    .field("snapshots", marketDataResult.snapshots().size())
                    .emit();
            } catch (RuntimeException exc) {
                failures.add("Exchange " + exchange.name() + " failed: " + exc.getMessage());
                eventLogger.error(BetxEventCategory.OPERATIONAL, "market.scan.failed")
                    .correlationId(cycleId)
                    .cycleId(cycleId)
                    .exchange(exchange.name())
                    .result("failed")
                    .field("errorType", exc.getClass().getSimpleName())
                    .field("message", safeMessage(exc))
                    .emit();
                dependencyError(cycleId, exchange.name(), "list_market_data", exc.getClass().getSimpleName(), safeMessage(exc));
            }
        }

        TelegramBetAlertSelection telegramAlertSelection = telegramBetAlertPolicy.select(telegramAlerts);
        if (sendTelegramAlerts && "all_signals".equals(config.telegram().alerts().mode())) {
            telegramAlertSelection.alertsToSend().forEach(alert -> {
                logTelegramAlertSend(alert);
                safeSendTelegramAlert(configPath, telegramBetAlertFormatter.format(alert));
            });
            telegramAlertSelection.skippedAlerts().forEach(this::logTelegramAlertSkip);
        } else if (sendTelegramAlerts
            && (!telegramAlertSelection.alertsToSend().isEmpty() || !telegramAlertSelection.skippedAlerts().isEmpty())) {
            emit(
                "TELEGRAM ALERTS SUPPRESSED | reason=mode_" + config.telegram().alerts().mode()
                    + " | alerts=" + telegramAlertSelection.alertsToSend().size()
                    + " | skipped=" + telegramAlertSelection.skippedAlerts().size()
            );
        } else if (logSuppressedTelegramAlerts
            && (!telegramAlertSelection.alertsToSend().isEmpty() || !telegramAlertSelection.skippedAlerts().isEmpty())) {
            emit(
                "TELEGRAM ALERTS SUPPRESSED | reason=startup_warmup"
                    + " | alerts=" + telegramAlertSelection.alertsToSend().size()
                    + " | skipped=" + telegramAlertSelection.skippedAlerts().size()
            );
        }
        cleanupExpiredMarketSnapshots(config);
        DryRunSignalsResult result = new DryRunSignalsResult(
            signals,
            failures,
            false,
            snapshotsSaved,
            comparisonsCalculated,
            changes,
            runnerAnalyses,
            intelligenceAssessments,
            signalHistoryEntries,
            marketsRead.size(),
            ignoredMarkets.size(),
            eventsRead,
            ignoredEvents
        );
        long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        eventLogger.info(BetxEventCategory.OPERATIONAL, "cycle.completed")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode(executionMode(sendTelegramAlerts, logSuppressedTelegramAlerts))
            .result(failures.isEmpty() ? "completed" : "completed_with_failures")
            .field("durationMs", durationMs)
            .field("eventsRead", eventsRead)
            .field("ignoredEvents", ignoredEvents)
            .field("marketsRead", marketsRead.size())
            .field("ignoredMarkets", ignoredMarkets.size())
            .field("runnersAnalyzed", runnerAnalyses.size())
            .field("signals", signals.size())
            .field("blocks", runnerAnalyses.size() - signals.size())
            .field("failures", failures.size())
            .emit();
        eventLogger.info(BetxEventCategory.ANALYTICS, "cycle.metrics.recorded")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .executionMode(executionMode(sendTelegramAlerts, logSuppressedTelegramAlerts))
            .result("recorded")
            .field("snapshotsSaved", snapshotsSaved)
            .field("comparisonsCalculated", comparisonsCalculated)
            .field("signalHistory", signalHistoryEntries.size())
            .emit();
        return result;
    }

    private void safeSendTelegramAlert(ConfigPath configPath, String text) {
        try {
            telegramService.sendMessageIfConnected(configPath, text, TelegramParseMode.HTML);
        } catch (RuntimeException exc) {
            emit("TELEGRAM ALERT WARNING | action=send_message | message=" + nullSafe(exc.getMessage()));
        }
    }

    private Optional<StrategyConfig> valueFootballStrategy(BetxConfig config) {
        return config.strategies().stream()
            .filter(strategyConfig -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategyConfig.name()))
            .findFirst();
    }

    private void cleanupExpiredMarketSnapshots(BetxConfig config) {
        if (!config.storage().cleanupMarketSnapshotsEnabled()) {
            return;
        }
        Instant cutoff = Instant.now(clock).minus(Duration.ofHours(config.storage().marketSnapshotRetentionHours()));
        int deleted = snapshotRepository.deleteExpiredMarkets(config.storage().path(), cutoff);
        if (deleted > 0) {
            emit("MARKET SNAPSHOTS CLEANED | reason=retention"
                + " | deleted=" + deleted
                + " | cutoff=" + cutoff);
        }
    }

    private BetSignal toSignal(RunnerAnalysis analysis, BetxConfig config) {
        return new BetSignal(
            analysis.exchange(),
            analysis.marketId(),
            analysis.selectionId(),
            BetSide.BACK,
            analysis.bestBackPrice(),
            config.risk().maxStake(),
            analysis.reason(),
            "signal",
            analysis.evaluationId()
        );
    }

    private void shadowPersistRecommendation(
        BetxConfig config,
        String cycleId,
        Instant observedAt,
        RunnerAnalysis analysis
    ) {
        BetRecommendation recommendation = toShadowRecommendation(observedAt, analysis);
        try {
            betRecommendationRepository.save(config.storage().path(), recommendation);
            logRecommendationCreated(cycleId, recommendation);
        } catch (RuntimeException exc) {
            eventLogger.warn(BetxEventCategory.ERROR, "bet_recommendation.persist_failed")
                .correlationId(signalCorrelationId(observedAt, analysis.exchange(), analysis.marketId(), analysis.selectionId()))
                .cycleId(cycleId)
                .exchange(analysis.exchange())
                .marketId(analysis.marketId())
                .selectionId(analysis.selectionId())
                .strategy(analysis.strategyName())
                .result("failed")
                .field("evaluationId", analysis.evaluationId())
                .field("errorType", exc.getClass().getSimpleName())
                .field("message", safeMessage(exc))
                .emit();
        }
    }

    private BetRecommendation toShadowRecommendation(Instant observedAt, RunnerAnalysis analysis) {
        return new BetRecommendation(
            UUID.randomUUID().toString(),
            analysis.evaluationId(),
            analysis.exchange(),
            analysis.marketId(),
            analysis.selectionId(),
            selectionSide(analysis),
            analysis.eventName(),
            analysis.displayRunner(),
            analysis.competitionName(),
            analysis.marketStartTime(),
            analysis.strategyName(),
            analysis.bestBackPrice(),
            observedAt,
            observedAt,
            BetRecommendationSource.SHADOW,
            BetRecommendationStatus.CREATED,
            Instant.now(clock),
            null,
            null,
            analysis.liquidity(),
            analysis.reason()
        );
    }

    private void logRecommendationCreated(String cycleId, BetRecommendation recommendation) {
        eventLogger.info(BetxEventCategory.ANALYTICS, "bet_recommendation.created")
            .correlationId("recommendation-" + recommendation.id())
            .cycleId(cycleId)
            .exchange(recommendation.exchange())
            .marketId(recommendation.marketId())
            .selectionId(recommendation.selectionId())
            .strategy(recommendation.strategyName())
            .executionMode("shadow")
            .result("created")
            .field("recommendationId", recommendation.id())
            .field("evaluationId", recommendation.evaluationId())
            .field("side", recommendation.selectionSide().name())
            .field("eventName", recommendation.eventName())
            .field("runnerName", recommendation.runnerName())
            .field("competitionName", recommendation.competitionName())
            .field("strategyName", recommendation.strategyName())
            .field("recommendedOdds", recommendation.recommendedOdds())
            .field("recommendedAt", recommendation.recommendedAt())
            .field("source", recommendation.source().name())
            .emit();
    }

    private SelectionSide selectionSide(RunnerAnalysis analysis) {
        SelectionSide fromRunnerType = switch (analysis.runnerType()) {
            case HOME -> SelectionSide.HOME;
            case DRAW -> SelectionSide.DRAW;
            case AWAY -> SelectionSide.AWAY;
            case UNKNOWN -> SelectionSide.UNKNOWN;
        };
        if (fromRunnerType != SelectionSide.UNKNOWN) {
            return fromRunnerType;
        }
        SelectionSide fromSelectionId = switch (BacktestRunnerType.fromSelectionId(analysis.selectionId())) {
            case HOME -> SelectionSide.HOME;
            case DRAW -> SelectionSide.DRAW;
            case AWAY -> SelectionSide.AWAY;
            case UNKNOWN -> SelectionSide.UNKNOWN;
        };
        if (fromSelectionId != SelectionSide.UNKNOWN) {
            return fromSelectionId;
        }
        return inferSelectionSide(analysis.eventName(), analysis.displayRunner());
    }

    private SelectionSide inferSelectionSide(String eventName, String runnerName) {
        if (runnerName == null || runnerName.isBlank()) {
            return SelectionSide.UNKNOWN;
        }
        String normalizedRunner = runnerName.strip();
        if (normalizedRunner.toLowerCase(java.util.Locale.ROOT).contains("draw")) {
            return SelectionSide.DRAW;
        }
        if (eventName == null || eventName.isBlank()) {
            return SelectionSide.UNKNOWN;
        }
        String[] teams = eventName.split("\\s+v\\s+", 2);
        if (teams.length != 2) {
            return SelectionSide.UNKNOWN;
        }
        if (normalizedRunner.equalsIgnoreCase(teams[0].strip())) {
            return SelectionSide.HOME;
        }
        if (normalizedRunner.equalsIgnoreCase(teams[1].strip())) {
            return SelectionSide.AWAY;
        }
        return SelectionSide.UNKNOWN;
    }

    private Optional<MatchIntelligenceAssessment> assessIntelligence(BetxConfig config, RunnerAnalysis analysis) {
        IntelligenceConfig intelligence = config.intelligence();
        if (!intelligence.enabled()) {
            return Optional.empty();
        }
        try {
            BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, analysis.exchange());
            return Optional.ofNullable(intelligenceGateway.assess(new MatchIntelligenceRequest(
                intelligence,
                analysis,
                autoBetting.enabled(),
                autoBetting.requestConfirmation()
            )));
        } catch (RuntimeException exc) {
            return Optional.of(MatchIntelligenceAssessment.unavailable(
                analysis.exchange(),
                analysis.marketId(),
                analysis.selectionId(),
                "External intelligence failed: " + exc.getMessage()
            ));
        }
    }

    private Optional<SignalHistoryEntry> saveSignalHistory(
        BetxConfig config,
        Instant observedAt,
        RunnerAnalysis analysis,
        Optional<MarketSnapshotChange> change,
        Optional<MatchIntelligenceAssessment> assessment
    ) {
        if (analysis.recommendation() == RecommendationType.NO_BET) {
            return Optional.empty();
        }
        SignalHistoryEntry entry = toSignalHistoryEntry(observedAt, analysis, change, assessment);
        try {
            signalHistoryRepository.saveDecision(config.storage().path(), entry);
        } catch (RuntimeException exc) {
            emit("SIGNAL HISTORY WARNING | action=save_decision"
                + " | exchange=" + analysis.exchange()
                + " | marketId=" + analysis.marketId()
                + " | selectionId=" + analysis.selectionId()
                + " | message=" + nullSafe(exc.getMessage()));
        }
        return Optional.of(entry);
    }

    private SignalHistoryEntry toSignalHistoryEntry(
        Instant observedAt,
        RunnerAnalysis analysis,
        Optional<MarketSnapshotChange> change,
        Optional<MatchIntelligenceAssessment> assessment
    ) {
        MatchIntelligenceAssessment intelligence = assessment.orElse(null);
        return new SignalHistoryEntry(
            observedAt,
            analysis.exchange(),
            analysis.marketId(),
            analysis.selectionId(),
            analysis.eventName(),
            analysis.marketName(),
            analysis.displayRunner(),
            analysis.competitionName(),
            analysis.marketStartTime(),
            analysis.recommendation(),
            analysis.score().value(),
            analysis.score().confidenceLabel(),
            analysis.reason(),
            analysis.bestBackPrice(),
            analysis.bestLayPrice(),
            analysis.spread(),
            analysis.liquidity(),
            change.map(MarketSnapshotChange::back).map(NumericChange::percentageDelta).orElse(null),
            change.map(MarketSnapshotChange::lay).map(NumericChange::percentageDelta).orElse(null),
            change.map(MarketSnapshotChange::liquidity).map(NumericChange::percentageDelta).orElse(null),
            intelligence == null ? null : intelligence.decision(),
            intelligence == null ? null : intelligence.confidence(),
            intelligence == null ? null : intelligence.summary(),
            null,
            null,
            null,
            null,
            null,
            null,
            analysis.evaluationId()
        );
    }

    private boolean blocksSignalForAutoBetting(
        BetxConfig config,
        String exchange,
        Optional<MatchIntelligenceAssessment> assessment
    ) {
        BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, exchange);
        if (!config.intelligence().enabled() || !autoBetting.enabled() || autoBetting.requestConfirmation()) {
            return false;
        }
        if (assessment.isEmpty()) {
            return true;
        }
        MatchIntelligenceDecision decision = assessment.get().decision();
        return switch (config.intelligence().autoBettingPolicy()) {
            case STRICT_APPROVE -> decision != MatchIntelligenceDecision.APPROVE;
            case BLOCK_ONLY_ON_REJECT ->
                decision == MatchIntelligenceDecision.REJECT || decision == MatchIntelligenceDecision.UNAVAILABLE;
        };
    }

    private BetfairAutoBettingConfig autoBettingConfig(BetxConfig config, String exchange) {
        return config.enabledExchanges().stream()
            .filter(exchangeConfig -> exchangeConfig.name().equals(exchange))
            .findFirst()
            .map(ExchangeConfig::betfair)
            .map(betfair -> betfair == null ? null : betfair.autoBetting())
            .orElseGet(() -> new BetfairAutoBettingConfig(false, true, null, null, null));
    }

    private void auditIntelligenceBlocked(BetxConfig config, RunnerAnalysis analysis, MatchIntelligenceAssessment assessment) {
        String decision = assessment == null ? "missing" : assessment.decision().name();
        emit("INTELLIGENCE BET BLOCKED | provider=openrouter"
            + " | policy=" + config.intelligence().autoBettingPolicy().configValue()
            + " | decision=" + decision
            + " | event=" + nullSafe(analysis.eventName())
            + " | runner=" + nullSafe(analysis.displayRunner())
            + " | marketId=" + analysis.marketId()
            + " | selectionId=" + analysis.selectionId());
    }

    private void auditIntelligenceAllowedByPolicy(
        BetxConfig config,
        RunnerAnalysis analysis,
        Optional<MatchIntelligenceAssessment> assessment
    ) {
        BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, analysis.exchange());
        if (!config.intelligence().enabled() || !autoBetting.enabled() || autoBetting.requestConfirmation()) {
            return;
        }
        if (config.intelligence().autoBettingPolicy() != IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT) {
            return;
        }
        if (assessment.map(MatchIntelligenceAssessment::decision).orElse(null) != MatchIntelligenceDecision.WATCH) {
            return;
        }
        emit("INTELLIGENCE BET ALLOWED | provider=openrouter"
            + " | policy=" + config.intelligence().autoBettingPolicy().configValue()
            + " | decision=WATCH"
            + " | event=" + nullSafe(analysis.eventName())
            + " | runner=" + nullSafe(analysis.displayRunner())
            + " | marketId=" + analysis.marketId()
            + " | selectionId=" + analysis.selectionId());
    }

    private void auditIntelligenceAssessment(MatchIntelligenceAssessment assessment) {
        eventLogger.info(BetxEventCategory.ANALYTICS, "intelligence.assessed")
            .correlationId("sig-" + assessment.exchange() + "-" + assessment.marketId() + "-" + assessment.selectionId())
            .exchange(assessment.exchange())
            .marketId(assessment.marketId())
            .selectionId(assessment.selectionId())
            .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
            .result(assessment.decision().name().toLowerCase(java.util.Locale.ROOT))
            .field("confidence", assessment.confidence())
            .field("summary", nullSafe(assessment.summary()))
            .emit();
        emit("INTELLIGENCE ASSESSMENT | provider=openrouter"
            + " | decision=" + assessment.decision()
            + " | confidence=" + assessment.confidence()
            + " | exchange=" + assessment.exchange()
            + " | marketId=" + assessment.marketId()
            + " | selectionId=" + assessment.selectionId()
            + " | summary=" + nullSafe(assessment.summary()));
    }

    private void logTelegramAlertSend(TelegramBetAlertCandidate alert) {
        emit(
            "TELEGRAM SIGNAL ALERT | trigger=" + alert.trigger().logLabel()
                + " | event=" + nullSafe(alert.analysis().eventName())
                + " | runner=" + nullSafe(alert.displayRunner())
                + " | marketId=" + alert.analysis().marketId()
                + " | selectionId=" + alert.analysis().selectionId()
        );
    }

    private void logTelegramAlertSkip(TelegramBetAlertSkip skippedAlert) {
        TelegramBetAlertCandidate alert = skippedAlert.candidate();
        emit(
            "TELEGRAM ALERT SKIPPED | reason=" + skippedAlert.reason()
                + " | trigger=" + alert.trigger().logLabel()
                + " | event=" + nullSafe(alert.analysis().eventName())
                + " | runner=" + nullSafe(alert.displayRunner())
                + " | marketId=" + alert.analysis().marketId()
                + " | selectionId=" + alert.analysis().selectionId()
        );
    }

    private void logSignalDecision(String cycleId, Instant observedAt, RunnerAnalysis analysis) {
        boolean accepted = analysis.recommendation() == RecommendationType.BET;
        eventLogger.info(BetxEventCategory.ANALYTICS, accepted ? "signal.generated" : "signal.rejected")
            .correlationId(signalCorrelationId(observedAt, analysis.exchange(), analysis.marketId(), analysis.selectionId()))
            .cycleId(cycleId)
            .exchange(analysis.exchange())
            .marketId(analysis.marketId())
            .selectionId(analysis.selectionId())
            .strategy(ValueFootballSignalStrategy.STRATEGY_NAME)
            .executionMode("scan")
            .result(accepted ? "accepted" : "rejected")
            .field("reason", analysis.reason())
            .field("evaluationId", analysis.evaluationId())
            .field("odds", analysis.bestBackPrice())
            .field("liquidity", analysis.liquidity())
            .field("runner", analysis.displayRunner())
            .emit();
    }

    private void dependencyError(String cycleId, String dependency, String action, String errorType, String message) {
        eventLogger.error(BetxEventCategory.ERROR, "dependency.error")
            .correlationId(cycleId)
            .cycleId(cycleId)
            .result("failed")
            .field("dependency", dependency)
            .field("action", action)
            .field("errorType", errorType)
            .field("message", message)
            .emit();
    }

    private String cycleId(Instant timestamp) {
        return "cycle-" + timestamp.toString().replace("-", "").replace(":", "").replace(".", "").replace("Z", "Z");
    }

    private String signalCorrelationId(Instant observedAt, String exchange, String marketId, long selectionId) {
        return "sig-" + exchange + "-" + marketId + "-" + selectionId + "-" + observedAt.toString();
    }

    private String executionMode(boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
        if (sendTelegramAlerts) {
            return "telegram_alerts";
        }
        return logSuppressedTelegramAlerts ? "startup_warmup" : "automatic";
    }

    private String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        return message == null || message.isBlank() ? exc.getClass().getSimpleName() : message;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void emit(String message) {
        output.get().accept(message);
    }

    private static final class NoopMarketSnapshotRepository implements MarketSnapshotRepository {
        @Override
        public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public List<ObservedMarketSnapshot> findRecent(String databasePath, String exchange, String marketId, long selectionId, int limit) {
            return List.of();
        }

        @Override
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
        }
    }

    private static final class NoopSignalHistoryRepository implements SignalHistoryRepository {
        @Override
        public void saveDecision(String databasePath, SignalHistoryEntry entry) {
        }

        @Override
        public void linkIntent(String databasePath, SignalHistoryKey key, com.betx.domain.order.BetIntent intent) {
        }

        @Override
        public void updateOrderState(String databasePath, com.betx.domain.order.BetIntent intent) {
        }
    }

    private static final class NoopBetRecommendationRepository implements BetRecommendationRepository {
        @Override
        public void save(String databasePath, BetRecommendation recommendation) {
        }

        @Override
        public Optional<BetRecommendation> findById(String databasePath, String id) {
            return Optional.empty();
        }

        @Override
        public List<BetRecommendation> findByEvaluationId(String databasePath, String evaluationId) {
            return List.of();
        }
    }
}
