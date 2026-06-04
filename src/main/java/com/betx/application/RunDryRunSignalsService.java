package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs one read-only multi-exchange cycle and emits strategy signals. */
@Service
public class RunDryRunSignalsService {
    private final BetxConfigRepository configRepository;
    private final Map<String, ExchangeMarketDataGateway> marketDataGateways;
    private final TelegramConnectionService telegramService;
    private final BetExecutionGateway executionGateway;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketSnapshotChangeDetector changeDetector;
    private final Clock clock;
    private final EventMarketAnalyzer analyzer;
    private final TelegramBetAlertFormatter telegramBetAlertFormatter;
    private final TelegramBetAlertPolicy telegramBetAlertPolicy;

    @Autowired
    public RunDryRunSignalsService(
        BetxConfigRepository configRepository,
        List<ExchangeMarketDataGateway> marketDataGateways,
        TelegramConnectionService telegramService,
        BetExecutionGateway executionGateway,
        MarketSnapshotRepository snapshotRepository,
        MarketSnapshotChangeDetector changeDetector
    ) {
        this(configRepository, marketDataGateways, telegramService, executionGateway, snapshotRepository, changeDetector, Clock.systemUTC());
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
        this.configRepository = configRepository;
        this.marketDataGateways = marketDataGateways.stream()
            .collect(Collectors.toMap(ExchangeMarketDataGateway::exchangeName, Function.identity(), (left, right) -> left));
        this.telegramService = telegramService;
        this.executionGateway = executionGateway;
        this.snapshotRepository = snapshotRepository;
        this.changeDetector = changeDetector;
        this.clock = clock;
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
            Clock.systemUTC()
        );
    }

    /** Runs one signal cycle across every enabled exchange. */
    public DryRunSignalsResult run(ConfigPath configPath) {
        return run(configPath, true);
    }

    public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts) {
        BetxConfig config = configRepository.load(configPath);

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
                continue;
            }
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
                    if (previous.isPresent()) {
                        comparisonsCalculated++;
                        changeDetector.compare(previous.get().snapshot(), snapshot).ifPresent(changes::add);
                    }
                    RunnerAnalysis analysis = analyzer.analyze(
                        snapshot,
                        previous.map(ObservedMarketSnapshot::snapshot),
                        strategyConfig.get(),
                        config.risk()
                    );
                    runnerAnalyses.add(analysis);
                    if (analysis.recommendation() == RecommendationType.BET) {
                        signals.add(toSignal(analysis, config));
                        telegramAlerts.add(TelegramBetAlertCandidate.from(analysis, previous.map(ObservedMarketSnapshot::snapshot)));
                    }
                    snapshotRepository.save(config.storage().path(), new ObservedMarketSnapshot(observedAt, snapshot));
                    snapshotsSaved++;
                }
            } catch (RuntimeException exc) {
                failures.add("Exchange " + exchange.name() + " failed: " + exc.getMessage());
            }
        }

        TelegramBetAlertSelection telegramAlertSelection = telegramBetAlertPolicy.select(telegramAlerts);
        if (sendTelegramAlerts) {
            telegramAlertSelection.alertsToSend().forEach(alert -> {
                logTelegramAlertSend(alert);
                telegramService.sendMessageIfConnected(configPath, telegramBetAlertFormatter.format(alert), TelegramParseMode.HTML);
            });
            telegramAlertSelection.skippedAlerts().forEach(this::logTelegramAlertSkip);
        } else if (!telegramAlertSelection.alertsToSend().isEmpty() || !telegramAlertSelection.skippedAlerts().isEmpty()) {
            System.out.println(
                "TELEGRAM ALERTS SUPPRESSED | reason=startup_warmup"
                    + " | alerts=" + telegramAlertSelection.alertsToSend().size()
                    + " | skipped=" + telegramAlertSelection.skippedAlerts().size()
            );
        }
        return new DryRunSignalsResult(
            signals,
            failures,
            false,
            snapshotsSaved,
            comparisonsCalculated,
            changes,
            runnerAnalyses,
            marketsRead.size(),
            ignoredMarkets.size(),
            eventsRead,
            ignoredEvents
        );
    }

    private Optional<StrategyConfig> valueFootballStrategy(BetxConfig config) {
        return config.strategies().stream()
            .filter(strategyConfig -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategyConfig.name()))
            .findFirst();
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
            "dry-run"
        );
    }

    private void logTelegramAlertSend(TelegramBetAlertCandidate alert) {
        System.out.println(
            "TELEGRAM ALERT DRY-RUN | trigger=" + alert.trigger().logLabel()
                + " | event=" + nullSafe(alert.analysis().eventName())
                + " | runner=" + nullSafe(alert.displayRunner())
                + " | marketId=" + alert.analysis().marketId()
                + " | selectionId=" + alert.analysis().selectionId()
        );
    }

    private void logTelegramAlertSkip(TelegramBetAlertSkip skippedAlert) {
        TelegramBetAlertCandidate alert = skippedAlert.candidate();
        System.out.println(
            "TELEGRAM ALERT SKIPPED | reason=" + skippedAlert.reason()
                + " | trigger=" + alert.trigger().logLabel()
                + " | event=" + nullSafe(alert.analysis().eventName())
                + " | runner=" + nullSafe(alert.displayRunner())
                + " | marketId=" + alert.analysis().marketId()
                + " | selectionId=" + alert.analysis().selectionId()
        );
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static final class NoopMarketSnapshotRepository implements MarketSnapshotRepository {
        @Override
        public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
        }
    }
}
