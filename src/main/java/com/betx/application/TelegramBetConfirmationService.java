package com.betx.application;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.application.port.out.ExchangeExposureGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.SignalHistoryRepository;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.application.port.out.BetIntentRepository;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.application.port.out.TelegramStateRepository;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.exposure.ExchangeExposure;
import com.betx.domain.exposure.ExchangeSettledOrder;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.RunnerType;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.telegram.TelegramConnectionContext;
import com.betx.domain.telegram.TelegramUpdate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TelegramBetConfirmationService {
    private static final Duration SELECTION_COOLDOWN = Duration.ofMinutes(30);
    private static final int ACTIVE_INTENT_EXPIRATION_SCAN_LIMIT = 500;
    private static final int EXECUTED_INTENT_RECONCILIATION_LIMIT = 500;
    private static final List<BetIntentStage> PENDING_CONFIRMATION_STAGES = List.of(
        BetIntentStage.AWAITING_CONFIRMATION,
        BetIntentStage.AWAITING_STAKE
    );
    private static final List<BigDecimal> STAKE_PRESETS = List.of(
        BigDecimal.valueOf(1),
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(5),
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(20),
        BigDecimal.valueOf(50)
    );

    private final BetxConfigRepository configRepository;
    private final TelegramConnectionService telegramConnectionService;
    private final TelegramBotGateway telegramGateway;
    private final BetIntentRepository intentRepository;
    private final TelegramStateRepository telegramStateRepository;
    private final ExchangeAccountGateway accountGateway;
    private final ExchangeExposureGateway exposureGateway;
    private final MarketSnapshotRepository snapshotRepository;
    private final SignalHistoryRepository signalHistoryRepository;
    private final BetExecutionGateway executionGateway;
    private final TelegramBetAlertFormatter telegramBetAlertFormatter;
    private final Clock clock;
    private final BetxEventLogger eventLogger;
    private final ThreadLocal<Consumer<String>> output = ThreadLocal.withInitial(() -> ignored -> {
    });

    @Autowired
    public TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        TelegramStateRepository telegramStateRepository,
        ExchangeAccountGateway accountGateway,
        @Qualifier("betfairExchangeExposureGateway") ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository signalHistoryRepository,
        @Qualifier("betfairBetExecutionGateway") BetExecutionGateway executionGateway,
        BetxEventLogger eventLogger
    ) {
        this(
            configRepository,
            telegramConnectionService,
            telegramGateway,
            intentRepository,
            telegramStateRepository,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            signalHistoryRepository,
            executionGateway,
            Clock.systemUTC(),
            eventLogger
        );
    }

    public TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway
    ) {
        this(
            configRepository,
            telegramConnectionService,
            telegramGateway,
            intentRepository,
            new NoopTelegramStateRepository(),
            accountGateway,
            (config, exchange, settledSince) -> ExchangeExposure.unavailable("Exposure gateway is not configured."),
            new NoopMarketSnapshotRepository(),
            new NoopSignalHistoryRepository(),
            executionGateway,
            Clock.systemUTC(),
            new BetxEventLogger(StructuredEventSink.noop())
        );
    }

    TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        TelegramStateRepository telegramStateRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        this(
            configRepository,
            telegramConnectionService,
            telegramGateway,
            intentRepository,
            telegramStateRepository,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            new NoopSignalHistoryRepository(),
            executionGateway,
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository signalHistoryRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        this(
            configRepository,
            telegramConnectionService,
            telegramGateway,
            intentRepository,
            new NoopTelegramStateRepository(),
            accountGateway,
            exposureGateway,
            snapshotRepository,
            signalHistoryRepository,
            executionGateway,
            clock
        );
    }

    TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        TelegramStateRepository telegramStateRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository signalHistoryRepository,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        this(
            configRepository,
            telegramConnectionService,
            telegramGateway,
            intentRepository,
            telegramStateRepository,
            accountGateway,
            exposureGateway,
            snapshotRepository,
            signalHistoryRepository,
            executionGateway,
            clock,
            new BetxEventLogger(StructuredEventSink.noop(), clock)
        );
    }

    TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        BetIntentRepository intentRepository,
        TelegramStateRepository telegramStateRepository,
        ExchangeAccountGateway accountGateway,
        ExchangeExposureGateway exposureGateway,
        MarketSnapshotRepository snapshotRepository,
        SignalHistoryRepository signalHistoryRepository,
        BetExecutionGateway executionGateway,
        Clock clock,
        BetxEventLogger eventLogger
    ) {
        this.configRepository = configRepository;
        this.telegramConnectionService = telegramConnectionService;
        this.telegramGateway = telegramGateway;
        this.intentRepository = intentRepository;
        this.telegramStateRepository = telegramStateRepository == null ? new NoopTelegramStateRepository() : telegramStateRepository;
        this.accountGateway = accountGateway;
        this.exposureGateway = exposureGateway;
        this.snapshotRepository = snapshotRepository == null ? new NoopMarketSnapshotRepository() : snapshotRepository;
        this.signalHistoryRepository = signalHistoryRepository == null ? new NoopSignalHistoryRepository() : signalHistoryRepository;
        this.executionGateway = executionGateway;
        this.telegramBetAlertFormatter = new TelegramBetAlertFormatter();
        this.clock = clock;
        this.eventLogger = eventLogger == null ? new BetxEventLogger(StructuredEventSink.noop(), clock) : eventLogger;
    }

    public void sync(ConfigPath configPath, DryRunSignalsResult result) {
        sync(configPath, result, ignored -> {
        });
    }

    public void sync(ConfigPath configPath, DryRunSignalsResult result, Consumer<String> outputConsumer) {
        output.set(outputConsumer == null ? ignored -> {
        } : outputConsumer);
        try {
            syncInternal(configPath, result);
        } finally {
            output.remove();
        }
    }

    private void syncInternal(ConfigPath configPath, DryRunSignalsResult result) {
        BetxConfig config = configRepository.load(configPath);
        eventLogger.configure(config.app());
        boolean confirmationRequired = confirmationRequired(config);
        expireStalePendingIntents(config);
        Map<String, ExchangeExposure> exposureByExchange = reconcileSettledIntents(config);
        Optional<TelegramConnectionContext> context = Optional.empty();
        if (config.telegram().enabled()) {
            context = telegramConnectionService.connectionContext(configPath);
            if (context.isPresent()) {
                processCallbacks(configPath, config, context.get());
            } else if (confirmationRequired && result != null && !result.signals().isEmpty()) {
                emit("TELEGRAM BET SYNC WARNING | action=connection_context | message=Telegram is not connected.");
            }
        }

        if (confirmationRequired) {
            context.ifPresent(ignored -> offerBetConfirmations(configPath, config, result));
            return;
        }

        executeAutomaticBets(configPath, config, result, exposureByExchange);
    }

    private void expireStalePendingIntents(BetxConfig config) {
        Instant expiresBefore = Instant.now(clock).minus(SELECTION_COOLDOWN);
        intentRepository.listByStages(
                config.storage().path(),
                PENDING_CONFIRMATION_STAGES,
                ACTIVE_INTENT_EXPIRATION_SCAN_LIMIT
            ).stream()
            .filter(intent -> intent.updatedAt().isBefore(expiresBefore))
            .forEach(intent -> {
                BetIntent expired = intent.withStageAt(
                    BetIntentStage.CANCELLED,
                    intent.availableBalance(),
                    intent.selectedStake(),
                    "Expired before confirmation.",
                    Instant.now(clock)
                );
                intentRepository.update(config.storage().path(), expired);
                safeUpdateSignalHistory(config, expired);
                logIntentEvent("bet_intent.expired", BetxEventCategory.AUDIT, "expired", expired)
                    .field("reason", "confirmation_timeout")
                    .emit();
                emit("BET INTENT EXPIRED | id=" + expired.id()
                    + " | exchange=" + expired.exchange()
                    + " | marketId=" + expired.marketId()
                    + " | selectionId=" + expired.selectionId());
            });
    }

    private Map<String, ExchangeExposure> reconcileSettledIntents(BetxConfig config) {
        Map<String, ExchangeExposure> exposureByExchange = new LinkedHashMap<>();
        config.enabledExchanges().forEach(exchange -> {
            ExchangeExposure exposure = exposureGateway.exposure(config, exchange.name(), todayStart());
            exposureByExchange.put(exchange.name(), exposure);
            if (exposure == null || !exposure.available()) {
                emit("TELEGRAM BET RECONCILIATION SKIPPED | reason=exposure_unavailable"
                    + " | exchange=" + exchange.name());
                return;
            }
            if (exposure.settledOrders().isEmpty()) {
                return;
            }
            Map<String, ExchangeSettledOrder> settledOrdersById = exposure.settledOrders().stream()
                .filter(order -> order.externalOrderId() != null && !order.externalOrderId().isBlank())
                .collect(Collectors.toMap(ExchangeSettledOrder::externalOrderId, Function.identity(), (left, right) -> left));
            intentRepository.listByStages(
                    config.storage().path(),
                    List.of(BetIntentStage.EXECUTED),
                    EXECUTED_INTENT_RECONCILIATION_LIMIT
                ).stream()
                .filter(intent -> intent.externalOrderId() != null)
                .filter(intent -> settledOrdersById.containsKey(intent.externalOrderId()))
                .forEach(intent -> {
                    ExchangeSettledOrder settledOrder = settledOrdersById.get(intent.externalOrderId());
                    BetIntent settled = intent.withSettlement(
                        BetIntentStage.SETTLED,
                        settlementResult(settledOrder.realizedProfitLoss()),
                        settledOrder.realizedProfitLoss(),
                        settledOrder.settledAt() == null ? Instant.now(clock) : settledOrder.settledAt(),
                        "Settled on exchange."
                    );
                    intentRepository.update(config.storage().path(), settled);
                    safeUpdateSignalHistory(config, settled);
                    int deletedSnapshots = config.storage().cleanupMarketSnapshotsEnabled()
                        ? snapshotRepository.deleteMarket(config.storage().path(), settled.exchange(), settled.marketId())
                        : 0;
                    emit("BET INTENT SETTLED | id=" + settled.id()
                        + " | exchange=" + settled.exchange()
                        + " | marketId=" + settled.marketId()
                        + " | selectionId=" + settled.selectionId()
                        + " | settlement=" + settled.settlementResult()
                        + " | pnl=" + numeric(settled.realizedProfitLoss()));
                    logIntentEvent("order.settled", BetxEventCategory.AUDIT, "settled", settled)
                        .field("settlement", settled.settlementResult())
                        .field("pnl", settled.realizedProfitLoss())
                        .emit();
                    if (deletedSnapshots > 0) {
                        emit("MARKET SNAPSHOTS CLEANED | reason=settled"
                            + " | deleted=" + deletedSnapshots
                            + " | exchange=" + settled.exchange()
                            + " | marketId=" + settled.marketId());
                    }
                });
        });
        return exposureByExchange;
    }

    private BetSettlementResult settlementResult(BigDecimal realizedProfitLoss) {
        BigDecimal pnl = realizedProfitLoss == null ? BigDecimal.ZERO : realizedProfitLoss;
        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            return BetSettlementResult.WIN;
        }
        if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            return BetSettlementResult.LOSE;
        }
        return BetSettlementResult.VOID;
    }

    private SelectionSide selectionSide(RunnerType runnerType) {
        if (runnerType == null) {
            return SelectionSide.UNKNOWN;
        }
        return switch (runnerType) {
            case HOME -> SelectionSide.HOME;
            case DRAW -> SelectionSide.DRAW;
            case AWAY -> SelectionSide.AWAY;
            case UNKNOWN -> SelectionSide.UNKNOWN;
        };
    }

    private void offerBetConfirmations(ConfigPath configPath, BetxConfig config, DryRunSignalsResult result) {
        Map<String, RunnerAnalysis> analysesByKey = result.runnerAnalyses().stream()
            .filter(analysis -> analysis.recommendation() == RecommendationType.BET)
            .collect(Collectors.toMap(this::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, MarketSnapshot> previousSnapshotsByKey = result.changes().stream()
            .collect(Collectors.toMap(
                change -> key(change.current().exchange(), change.current().marketId(), change.current().selectionId()),
                MarketSnapshotChange::previous,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, MatchIntelligenceAssessment> intelligenceByKey = result.intelligenceAssessments().stream()
            .collect(Collectors.toMap(
                assessment -> key(assessment.exchange(), assessment.marketId(), assessment.selectionId()),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, SignalHistoryEntry> historyByKey = historyByKey(result);
        for (BetSignal signal : result.signals()) {
            String key = key(signal.exchange(), signal.marketId(), signal.selectionId());
            RunnerAnalysis analysis = analysesByKey.get(key);
            if (analysis == null) {
                continue;
            }
            if (intentRepository.findActiveByKey(config.storage().path(), signal.exchange(), signal.marketId(), signal.selectionId()).isPresent()) {
                auditSkipped("active_intent_exists", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            Instant now = Instant.now(clock);
            if (intentRepository.findLatestByKeySince(
                config.storage().path(),
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                now.minus(SELECTION_COOLDOWN)
            ).isPresent()) {
                auditSkipped("cooldown", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, signal.exchange());

            eventLogger.info(BetxEventCategory.AUDIT, "telegram.confirmation.requested")
                .correlationId(signalCorrelationId(signal))
                .exchange(signal.exchange())
                .marketId(signal.marketId())
                .selectionId(signal.selectionId())
                .strategy("value-football")
                .executionMode("telegram_confirmation")
                .result("requested")
                .field("odds", signal.odds())
                .emit();
            BetIntent intent = new BetIntent(
                UUID.randomUUID().toString(),
                BetIntentSource.TELEGRAM_CONFIRMATION,
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                analysis.eventName(),
                analysis.marketName(),
                analysis.displayRunner(),
                analysis.competitionName(),
                selectionSide(analysis.runnerType()),
                analysis.strategyName(),
                signal.reason(),
                signal.odds(),
                autoBetting.maxStake(),
                null,
                null,
                null,
                null,
                BetIntentStage.AWAITING_CONFIRMATION,
                now,
                now
            );
            boolean sent = safeTelegramSend(
                configPath,
                telegramBetAlertFormatter.formatLiveConfirmation(
                    analysis,
                    Optional.ofNullable(previousSnapshotsByKey.get(key)),
                    Optional.ofNullable(intelligenceByKey.get(key))
                ),
                TelegramParseMode.HTML,
                confirmationKeyboard(intent.id())
            );
            if (!sent) {
                auditSkipped("telegram_send_failed", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            intentRepository.save(config.storage().path(), intent);
            linkHistoryForSignal(config, historyByKey, signal, intent);
            logIntentEvent("telegram.confirmation.sent", BetxEventCategory.AUDIT, "sent", intent).emit();
            auditCreated(intent);
        }
    }

    private void executeAutomaticBets(
        ConfigPath configPath,
        BetxConfig config,
        DryRunSignalsResult result,
        Map<String, ExchangeExposure> exposureByExchange
    ) {
        if (result == null || result.signals().isEmpty()) {
            return;
        }
        Map<String, RunnerAnalysis> analysesByKey = result.runnerAnalyses().stream()
            .filter(analysis -> analysis.recommendation() == RecommendationType.BET)
            .collect(Collectors.toMap(this::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, SignalHistoryEntry> historyByKey = historyByKey(result);
        Map<String, AutomaticBettingExchangeState> exchangeStates = new LinkedHashMap<>();
        OrderExecutionCoordinator orderExecutionCoordinator = new OrderExecutionCoordinator(clock);
        java.util.Set<String> processedMarketKeys = new java.util.HashSet<>();

        for (BetSignal signal : result.signals()) {
            BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, signal.exchange());
            if (!autoBetting.enabled() || autoBetting.requestConfirmation()) {
                continue;
            }
            String key = key(signal.exchange(), signal.marketId(), signal.selectionId());
            RunnerAnalysis analysis = analysesByKey.get(key);
            if (analysis == null) {
                continue;
            }
            Instant now = Instant.now(clock);
            String marketKey = marketKey(signal.exchange(), signal.marketId());
            if (intentRepository.findActiveByMarket(config.storage().path(), signal.exchange(), signal.marketId()).isPresent()) {
                auditSkipped("active_market_intent_exists", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            if (intentRepository.findLatestByMarketSince(
                config.storage().path(),
                signal.exchange(),
                signal.marketId(),
                now.minus(SELECTION_COOLDOWN)
            ).isPresent()) {
                auditSkipped("market_cooldown", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            if (!processedMarketKeys.add(marketKey)) {
                auditSkipped("market_cycle_limit", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            if (intentRepository.findActiveByKey(config.storage().path(), signal.exchange(), signal.marketId(), signal.selectionId()).isPresent()) {
                auditSkipped("active_intent_exists", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            if (intentRepository.findLatestByKeySince(
                config.storage().path(),
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                now.minus(SELECTION_COOLDOWN)
            ).isPresent()) {
                auditSkipped("cooldown", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }
            AutomaticBettingExchangeState exchangeState = exchangeStates.computeIfAbsent(
                signal.exchange(),
                ignored -> automaticBettingExchangeState(config, signal.exchange(), autoBetting, exposureByExchange)
            );
            if (exchangeState.closed()) {
                continue;
            }
            Optional<RiskBlock> riskBlock = exchangeState.riskBlock();
            BigDecimal stake = maxAllowedStake(config, exchangeState.availableBalance(), autoBetting.maxStake());
            if (riskBlock.isPresent()) {
                eventLogger.warn(BetxEventCategory.AUDIT, "risk.blocked")
                    .correlationId(signalCorrelationId(signal))
                    .exchange(signal.exchange())
                    .marketId(signal.marketId())
                    .selectionId(signal.selectionId())
                    .strategy("value-football")
                    .executionMode("automatic")
                    .result("blocked")
                    .field("reason", riskBlock.get().reason())
                    .field("availableBalance", exchangeState.availableBalance())
                    .field("selectedStake", stake)
                    .emit();
                auditSkipped(riskBlock.get().reason(), signal.exchange(), signal.marketId(), signal.selectionId());
                if ("max_open_positions".equals(riskBlock.get().reason())) {
                    exchangeState.close();
                    continue;
                }
                BetIntent intent = saveAutomaticBlockedIntent(
                    config,
                    signal,
                    analysis,
                    autoBetting,
                    exchangeState.availableBalance(),
                    stake,
                    riskBlock.get().message(),
                    now
                );
                linkHistoryForSignal(config, historyByKey, signal, intent);
                safeUpdateSignalHistory(config, intent);
                exchangeState.close();
                continue;
            }
            if (exchangeState.remainingOpenPositionCapacity() <= 0) {
                auditSkipped("max_open_positions", signal.exchange(), signal.marketId(), signal.selectionId());
                exchangeState.close();
                continue;
            }
            if (exchangeState.availableBalance() == null || exchangeState.availableBalance().compareTo(BigDecimal.ZERO) <= 0) {
                auditSkipped("balance_unavailable", signal.exchange(), signal.marketId(), signal.selectionId());
            }
            OrderExecutionCoordinator.OrderExecutionReservation reservation = orderExecutionCoordinator.reserve(
                config.execution().queue(),
                signal.exchange(),
                exchangeState.availableBalance(),
                stake,
                now
            );
            if (!reservation.allowed()) {
                eventLogger.warn(BetxEventCategory.AUDIT, "risk.blocked")
                    .correlationId(signalCorrelationId(signal))
                    .exchange(signal.exchange())
                    .marketId(signal.marketId())
                    .selectionId(signal.selectionId())
                    .strategy("value-football")
                    .executionMode("automatic")
                    .result("blocked")
                    .field("reason", "execution_queue_block")
                    .field("availableBalance", reservation.availableBalance())
                    .field("effectiveAvailableBalance", reservation.effectiveAvailableBalance())
                    .field("reservedBalance", reservation.reservedBalance())
                    .field("selectedStake", reservation.stake())
                    .field("message", reservation.blockMessage())
                    .emit();
                auditSkipped("execution_queue_block", signal.exchange(), signal.marketId(), signal.selectionId());
                BetIntent intent = saveAutomaticBlockedIntent(
                    config,
                    signal,
                    analysis,
                    autoBetting,
                    reservation,
                    reservation.blockMessage(),
                    now
                );
                linkHistoryForSignal(config, historyByKey, signal, intent);
                safeUpdateSignalHistory(config, intent);
                if ("Balance unavailable. Bet blocked for safety.".equals(reservation.blockMessage())
                    || "Effective balance unavailable. Bet blocked for safety.".equals(reservation.blockMessage())) {
                    exchangeState.close();
                }
                continue;
            }
            eventLogger.info(BetxEventCategory.AUDIT, "risk.approved")
                .correlationId(signalCorrelationId(signal))
                .exchange(signal.exchange())
                .marketId(signal.marketId())
                .selectionId(signal.selectionId())
                .strategy("value-football")
                .executionMode("automatic")
                .result("approved")
                .field("availableBalance", reservation.availableBalance())
                .field("effectiveAvailableBalance", reservation.effectiveAvailableBalance())
                .field("reservedBalance", reservation.reservedBalance())
                .field("selectedStake", reservation.stake())
                .emit();
            BetOrder order = new BetOrder(
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                signal.side(),
                signal.odds(),
                stake
            );
            BetExecutionResult execution;
            try {
                eventLogger.info(BetxEventCategory.AUDIT, "order.submitted")
                    .correlationId(signalCorrelationId(signal))
                    .exchange(signal.exchange())
                    .marketId(signal.marketId())
                    .selectionId(signal.selectionId())
                    .strategy("value-football")
                    .executionMode("automatic")
                    .result("submitted")
                    .field("stake", stake)
                    .field("odds", signal.odds())
                    .emit();
                execution = executionGateway.execute(configPath, order);
            } catch (RuntimeException exc) {
                execution = BetExecutionResult.rejected("Order execution failed: " + safeMessage(exc));
                dependencyError("betfair", "place_order", exc);
            }
            orderExecutionCoordinator.complete(reservation, execution.accepted());
            BetIntent intent = new BetIntent(
                UUID.randomUUID().toString(),
                BetIntentSource.AUTOMATIC,
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                analysis.eventName(),
                analysis.marketName(),
                analysis.displayRunner(),
                analysis.competitionName(),
                selectionSide(analysis.runnerType()),
                analysis.strategyName(),
                signal.reason(),
                signal.odds(),
                autoBetting.maxStake(),
                reservation.availableBalance(),
                reservation.effectiveAvailableBalance(),
                reservation.reservedBalance(),
                reservation.balanceSnapshotAt(),
                stake,
                execution.message(),
                execution.externalOrderId(),
                execution.accepted() ? BetIntentStage.EXECUTED : BetIntentStage.FAILED,
                now,
                now
            );
            intentRepository.save(config.storage().path(), intent);
            linkHistoryForSignal(config, historyByKey, signal, intent);
            safeUpdateSignalHistory(config, intent);
            if (execution.accepted()) {
                exchangeState.recordAcceptedOrder();
                logIntentEvent("order.accepted", BetxEventCategory.AUDIT, "accepted", intent)
                    .field("stake", stake)
                    .field("externalOrderId", execution.externalOrderId())
                    .emit();
                emit("AUTO BET ORDER ACCEPTED | id=" + intent.id()
                    + " | stake=" + stake
                    + " | exchange=" + intent.exchange()
                    + " | marketId=" + intent.marketId()
                    + " | selectionId=" + intent.selectionId());
                safeTelegramSend(
                    configPath,
                    formatAutomaticBetPlacedMessage(intent, analysis),
                    TelegramParseMode.HTML,
                    null
                );
                if (exchangeState.remainingOpenPositionCapacity() <= 0) {
                    exchangeState.close();
                }
            } else {
                logIntentEvent("order.rejected", BetxEventCategory.AUDIT, "rejected", intent)
                    .field("message", execution.message())
                    .emit();
                emit("AUTO BET ORDER REJECTED | id=" + intent.id()
                    + " | message=" + execution.message()
                    + " | exchange=" + intent.exchange()
                    + " | marketId=" + intent.marketId()
                    + " | selectionId=" + intent.selectionId());
            }
        }
    }

    private AutomaticBettingExchangeState automaticBettingExchangeState(
        BetxConfig config,
        String exchange,
        BetfairAutoBettingConfig autoBetting,
        Map<String, ExchangeExposure> exposureByExchange
    ) {
        ExchangeExposure exposure = exposureByExchange == null || !exposureByExchange.containsKey(exchange)
            ? exposureGateway.exposure(config, exchange, todayStart())
            : exposureByExchange.get(exchange);
        Optional<RiskBlock> riskBlock = exposureRiskBlock(autoBetting, exposure);
        BigDecimal availableBalance = null;
        if (riskBlock.isEmpty()) {
            try {
                availableBalance = accountGateway.availableBalance(config, exchange).orElse(null);
            } catch (RuntimeException exc) {
                riskBlock = Optional.of(new RiskBlock("balance_unavailable", "Balance unavailable. Bet blocked for safety."));
                auditTelegramFailure("available_balance", exc);
            }
        }
        int openPositions = exposure == null || !exposure.available() ? 0 : exposure.openPositions();
        return new AutomaticBettingExchangeState(autoBetting.maxOpenPositions(), openPositions, availableBalance, riskBlock);
    }

    private void processCallbacks(ConfigPath configPath, BetxConfig config, TelegramConnectionContext context) {
        long lastUpdateId = telegramStateRepository.loadLastProcessedUpdateId(config.storage().path());
        List<TelegramUpdate> updates;
        try {
            updates = telegramGateway.getUpdates(context.token(), lastUpdateId == 0L ? null : lastUpdateId + 1L, 0);
        } catch (RuntimeException exc) {
            auditTelegramFailure("get_updates", exc);
            return;
        }
        long maxUpdateId = lastUpdateId;

        for (TelegramUpdate update : updates) {
            maxUpdateId = Math.max(maxUpdateId, update.updateId());
            if (update.hasCallbackQuery()) {
                eventLogger.info(BetxEventCategory.AUDIT, "telegram.callback.received")
                    .correlationId("telegram-update-" + update.updateId())
                    .executionMode("telegram_confirmation")
                    .result("received")
                    .field("updateId", update.updateId())
                    .emit();
                try {
                    handleCallback(configPath, config, update);
                } catch (RuntimeException exc) {
                    auditTelegramFailure("callback_processing", exc);
                }
            }
        }

        if (maxUpdateId > lastUpdateId) {
            telegramStateRepository.saveLastProcessedUpdateId(config.storage().path(), maxUpdateId);
        }
    }

    private void handleCallback(ConfigPath configPath, BetxConfig config, TelegramUpdate update) {
        CallbackAction action = CallbackAction.parse(update.callbackData());
        if (action == null) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Unsupported action.", false);
            return;
        }

        Optional<BetIntent> intentOptional = intentRepository.findById(config.storage().path(), action.intentId());
        if (intentOptional.isEmpty()) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Intent not found.", false);
            return;
        }

        BetIntent intent = intentOptional.get();
        switch (action.type()) {
            case YES -> handleYes(configPath, config, update, intent);
            case NO, CANCEL -> handleCancel(configPath, config, update, intent);
            case STAKE -> handleStake(configPath, config, update, intent, action.amount());
        }
    }

    private void handleYes(ConfigPath configPath, BetxConfig config, TelegramUpdate update, BetIntent intent) {
        if (!intent.stage().isActive() || intent.stage() != BetIntentStage.AWAITING_CONFIRMATION) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Already processed.", false);
            return;
        }

        Optional<BigDecimal> availableBalance = accountGateway.availableBalance(config, intent.exchange());
        if (availableBalance.isEmpty() || availableBalance.get().compareTo(BigDecimal.ZERO) <= 0) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Balance unavailable.", true);
            return;
        }

        BetIntent updated = intent.withStageAt(
            BetIntentStage.AWAITING_STAKE,
            availableBalance.get(),
            null,
            "Stake selection requested.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        safeUpdateSignalHistory(config, updated);
        logIntentEvent("bet_intent.stage_changed", BetxEventCategory.AUDIT, "awaiting_stake", updated)
            .field("stage", updated.stage())
            .emit();
        emit("TELEGRAM BET STAKE REQUESTED | id=" + updated.id()
            + " | exchange=" + updated.exchange()
            + " | marketId=" + updated.marketId()
            + " | selectionId=" + updated.selectionId());

        BigDecimal maxAllowed = maxAllowedStake(config, updated);
        safeTelegramEdit(
            configPath,
            update.messageId(),
            formatStakeSelectionMessage(updated, maxAllowed),
            TelegramParseMode.HTML,
            stakeKeyboard(updated.id(), maxAllowed)
        );
        safeTelegramAnswer(configPath, update.callbackQueryId(), "Choose a stake.", false);
    }

    private void handleCancel(ConfigPath configPath, BetxConfig config, TelegramUpdate update, BetIntent intent) {
        BetIntent updated = intent.withStageAt(
            BetIntentStage.CANCELLED,
            intent.availableBalance(),
            intent.selectedStake(),
            "Cancelled by Telegram user.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        safeUpdateSignalHistory(config, updated);
        logIntentEvent("telegram.confirmation.cancelled", BetxEventCategory.AUDIT, "cancelled", updated).emit();
        logIntentEvent("bet_intent.stage_changed", BetxEventCategory.AUDIT, "cancelled", updated)
            .field("stage", updated.stage())
            .emit();
        emit("BET INTENT CANCELLED | id=" + updated.id()
            + " | exchange=" + updated.exchange()
            + " | marketId=" + updated.marketId()
            + " | selectionId=" + updated.selectionId());
        safeTelegramEdit(
            configPath,
            update.messageId(),
            formatCancelledMessage(updated),
            TelegramParseMode.HTML,
            Map.of("inline_keyboard", List.of())
        );
        safeTelegramAnswer(configPath, update.callbackQueryId(), "Cancelled.", false);
    }

    private void handleStake(ConfigPath configPath, BetxConfig config, TelegramUpdate update, BetIntent intent, BigDecimal amount) {
        if (intent.stage() != BetIntentStage.AWAITING_STAKE) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Stake already resolved.", false);
            return;
        }

        BigDecimal maxAllowed = maxAllowedStake(config, intent);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxAllowed) > 0) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Amount not allowed.", true);
            return;
        }

        BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, intent.exchange());
        if (!autoBetting.enabled()) {
            String message = "Auto-betting is disabled.";
            BetIntent updated = intent.withStageAt(
                BetIntentStage.FAILED,
                intent.availableBalance(),
                amount,
                message,
                Instant.now(clock)
            );
            intentRepository.update(config.storage().path(), updated);
            safeUpdateSignalHistory(config, updated);
            emit("TELEGRAM BET EXECUTION BLOCKED | reason=auto_betting_disabled"
                + " | id=" + intent.id()
                + " | exchange=" + intent.exchange()
                + " | marketId=" + intent.marketId()
                + " | selectionId=" + intent.selectionId());
            safeTelegramEdit(
                configPath,
                update.messageId(),
                formatRejectedMessage(updated, message),
                TelegramParseMode.HTML,
                Map.of("inline_keyboard", List.of())
            );
            safeTelegramAnswer(configPath, update.callbackQueryId(), message, true);
            return;
        }

        Optional<RiskBlock> riskBlock = exposureRiskBlock(config, intent.exchange());
        if (riskBlock.isPresent()) {
            eventLogger.warn(BetxEventCategory.AUDIT, "risk.blocked")
                .correlationId("intent-" + intent.id())
                .exchange(intent.exchange())
                .marketId(intent.marketId())
                .selectionId(intent.selectionId())
                .strategy("value-football")
                .executionMode("telegram_confirmation")
                .result("blocked")
                .field("reason", riskBlock.get().reason())
                .field("selectedStake", amount)
                .emit();
            blockExecution(configPath, config, update, intent, amount, riskBlock.get().reason(), riskBlock.get().message());
            return;
        }

        BetOrder order = new BetOrder(
            intent.exchange(),
            intent.marketId(),
            intent.selectionId(),
            com.betx.domain.signal.BetSide.BACK,
            intent.odds(),
            amount
        );
        eventLogger.info(BetxEventCategory.AUDIT, "order.submitted")
            .correlationId("intent-" + intent.id())
            .exchange(intent.exchange())
            .marketId(intent.marketId())
            .selectionId(intent.selectionId())
            .strategy("value-football")
            .executionMode("telegram_confirmation")
            .result("submitted")
            .field("stake", amount)
            .field("odds", intent.odds())
            .emit();
        var result = executionGateway.execute(configPath, order);
        BetIntent updated = intent.withStageAt(
            result.accepted() ? BetIntentStage.EXECUTED : BetIntentStage.FAILED,
            intent.availableBalance(),
            amount,
            result.message(),
            Instant.now(clock)
        ).withExternalOrderId(result.externalOrderId());
        intentRepository.update(config.storage().path(), updated);
        safeUpdateSignalHistory(config, updated);
        if (result.accepted()) {
            logIntentEvent("order.accepted", BetxEventCategory.AUDIT, "accepted", updated)
                .field("stake", amount)
                .field("externalOrderId", result.externalOrderId())
                .emit();
            emit("TELEGRAM BET ORDER ACCEPTED | id=" + updated.id()
                + " | stake=" + amount
                + " | exchange=" + updated.exchange()
                + " | marketId=" + updated.marketId()
                + " | selectionId=" + updated.selectionId());
        } else {
            logIntentEvent("order.rejected", BetxEventCategory.AUDIT, "rejected", updated)
                .field("message", result.message())
                .emit();
            emit("TELEGRAM BET ORDER REJECTED | id=" + updated.id()
                + " | message=" + result.message()
                + " | exchange=" + updated.exchange()
                + " | marketId=" + updated.marketId()
                + " | selectionId=" + updated.selectionId());
        }
        safeTelegramEdit(
            configPath,
            update.messageId(),
            result.accepted() ? formatExecutedMessage(updated, amount) : formatRejectedMessage(updated, result.message()),
            TelegramParseMode.HTML,
            Map.of("inline_keyboard", List.of())
        );
        safeTelegramAnswer(configPath, update.callbackQueryId(), result.message(), !result.accepted());
    }

    private void blockExecution(
        ConfigPath configPath,
        BetxConfig config,
        TelegramUpdate update,
        BetIntent intent,
        BigDecimal amount,
        String reason,
        String message
    ) {
        BetIntent updated = intent.withStageAt(
            BetIntentStage.FAILED,
            intent.availableBalance(),
            amount,
            message,
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        safeUpdateSignalHistory(config, updated);
        logIntentEvent("risk.blocked", BetxEventCategory.AUDIT, "blocked", updated)
            .field("reason", reason)
            .field("selectedStake", amount)
            .emit();
        emit("TELEGRAM BET EXECUTION BLOCKED | reason=" + reason
            + " | id=" + updated.id()
            + " | exchange=" + updated.exchange()
            + " | marketId=" + updated.marketId()
            + " | selectionId=" + updated.selectionId());
        safeTelegramEdit(
            configPath,
            update.messageId(),
            formatRejectedMessage(updated, message),
            TelegramParseMode.HTML,
            Map.of("inline_keyboard", List.of())
        );
        safeTelegramAnswer(configPath, update.callbackQueryId(), message, true);
    }

    private boolean safeTelegramSend(
        ConfigPath configPath,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        try {
            boolean sent = telegramConnectionService.sendMessageIfConnected(configPath, text, parseMode, replyMarkup);
            if (!sent) {
                auditTelegramFailure("send_message", new IllegalStateException("Telegram is not connected."));
            }
            return sent;
        } catch (RuntimeException exc) {
            auditTelegramFailure("send_message", exc);
            return false;
        }
    }

    private void safeTelegramEdit(
        ConfigPath configPath,
        Integer messageId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        try {
            telegramConnectionService.editMessageIfConnected(configPath, messageId, text, parseMode, replyMarkup);
        } catch (RuntimeException exc) {
            auditTelegramFailure("edit_message", exc);
        }
    }

    private void safeTelegramAnswer(ConfigPath configPath, String callbackQueryId, String text, boolean showAlert) {
        try {
            telegramConnectionService.answerCallbackIfConnected(configPath, callbackQueryId, text, showAlert);
        } catch (RuntimeException exc) {
            auditTelegramFailure("answer_callback", exc);
        }
    }

    private Optional<RiskBlock> exposureRiskBlock(BetxConfig config, String exchange) {
        BetfairAutoBettingConfig autoBetting = autoBettingConfig(config, exchange);
        ExchangeExposure exposure = exposureGateway.exposure(config, exchange, todayStart());
        return exposureRiskBlock(autoBetting, exposure);
    }

    private Optional<RiskBlock> exposureRiskBlock(BetfairAutoBettingConfig autoBetting, ExchangeExposure exposure) {
        if (exposure == null || !exposure.available()) {
            return Optional.of(new RiskBlock("exposure_unavailable", "Exposure unavailable. Bet blocked for safety."));
        }
        if (exposure.openPositions() >= autoBetting.maxOpenPositions()) {
            return Optional.of(new RiskBlock("max_open_positions", "Open position limit reached."));
        }
        BigDecimal realizedLoss = exposure.realizedProfitLoss().compareTo(BigDecimal.ZERO) < 0
            ? exposure.realizedProfitLoss().abs()
            : BigDecimal.ZERO;
        if (realizedLoss.compareTo(autoBetting.maxDailyLoss()) >= 0) {
            return Optional.of(new RiskBlock("max_daily_loss", "Daily realized loss limit reached."));
        }
        return Optional.empty();
    }

    private BetIntent saveAutomaticBlockedIntent(
        BetxConfig config,
        BetSignal signal,
        RunnerAnalysis analysis,
        BetfairAutoBettingConfig autoBetting,
        BigDecimal availableBalance,
        BigDecimal stake,
        String message,
        Instant now
    ) {
        BetIntent intent = new BetIntent(
            UUID.randomUUID().toString(),
            BetIntentSource.AUTOMATIC,
            signal.exchange(),
            signal.marketId(),
            signal.selectionId(),
            analysis.eventName(),
            analysis.marketName(),
            analysis.displayRunner(),
            analysis.competitionName(),
            selectionSide(analysis.runnerType()),
            analysis.strategyName(),
            signal.reason(),
            signal.odds(),
            autoBetting.maxStake(),
            availableBalance,
            stake,
            message,
            null,
            BetIntentStage.FAILED,
            now,
            now
        );
        intentRepository.save(config.storage().path(), intent);
        return intent;
    }

    private BetIntent saveAutomaticBlockedIntent(
        BetxConfig config,
        BetSignal signal,
        RunnerAnalysis analysis,
        BetfairAutoBettingConfig autoBetting,
        OrderExecutionCoordinator.OrderExecutionReservation reservation,
        String message,
        Instant now
    ) {
        BetIntent intent = new BetIntent(
            UUID.randomUUID().toString(),
            BetIntentSource.AUTOMATIC,
            signal.exchange(),
            signal.marketId(),
            signal.selectionId(),
            analysis.eventName(),
            analysis.marketName(),
            analysis.displayRunner(),
            analysis.competitionName(),
            selectionSide(analysis.runnerType()),
            analysis.strategyName(),
            signal.reason(),
            signal.odds(),
            autoBetting.maxStake(),
            reservation.availableBalance(),
            reservation.effectiveAvailableBalance(),
            reservation.reservedBalance(),
            reservation.balanceSnapshotAt(),
            reservation.stake(),
            message,
            null,
            BetIntentStage.FAILED,
            now,
            now
        );
        intentRepository.save(config.storage().path(), intent);
        return intent;
    }

    private Map<String, SignalHistoryEntry> historyByKey(DryRunSignalsResult result) {
        if (result == null) {
            return Map.of();
        }
        return result.signalHistoryEntries().stream()
            .collect(Collectors.toMap(
                entry -> key(entry.exchange(), entry.marketId(), entry.selectionId()),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private void linkHistoryForSignal(
        BetxConfig config,
        Map<String, SignalHistoryEntry> historyByKey,
        BetSignal signal,
        BetIntent intent
    ) {
        SignalHistoryEntry entry = historyByKey.get(key(signal.exchange(), signal.marketId(), signal.selectionId()));
        if (entry == null) {
            return;
        }
        try {
            signalHistoryRepository.linkIntent(config.storage().path(), entry.key(), intent);
        } catch (RuntimeException exc) {
            auditSignalHistoryFailure("link_intent", intent, exc);
        }
    }

    private void safeUpdateSignalHistory(BetxConfig config, BetIntent intent) {
        try {
            signalHistoryRepository.updateOrderState(config.storage().path(), intent);
        } catch (RuntimeException exc) {
            auditSignalHistoryFailure("update_order_state", intent, exc);
        }
    }

    private void auditSignalHistoryFailure(String action, BetIntent intent, RuntimeException exc) {
        String message = safeMessage(exc);
        dependencyError("sqlite", action, exc);
        emit("SIGNAL HISTORY WARNING | action=" + action
            + " | intentId=" + intent.id()
            + " | exchange=" + intent.exchange()
            + " | marketId=" + intent.marketId()
            + " | selectionId=" + intent.selectionId()
            + " | message=" + message);
    }

    private String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        return message == null || message.isBlank() ? exc.getClass().getSimpleName() : message;
    }

    private void emit(String message) {
        output.get().accept(message);
    }

    private Instant todayStart() {
        return Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private BigDecimal maxAllowedStake(BetxConfig config, BetIntent intent) {
        return maxAllowedStake(config, intent.availableBalance(), intent.maxStake());
    }

    private BigDecimal maxAllowedStake(BetxConfig config, BigDecimal availableBalance, BigDecimal configuredMax) {
        BigDecimal maxStake = configuredMax == null ? config.risk().maxStake() : configuredMax;
        if (availableBalance == null) {
            return maxStake;
        }
        return maxStake.min(availableBalance).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatAutomaticBetPlacedMessage(BetIntent intent, RunnerAnalysis analysis) {
        return "<b>BETX ORDER</b>\n"
            + "REAL BET PLACED\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), analysis.eventName())) + "</b>\n"
            + "Selection: " + escape(textOrDefault(intent.runnerName(), analysis.displayRunner())) + "\n"
            + "Action: " + intent.side() + " on " + escape(intent.exchange()) + "\n"
            + "Odds: " + numeric(intent.odds()) + "\n"
            + "Stake: " + numeric(intent.selectedStake()) + "\n"
            + "Balance available: " + numeric(intent.availableBalance()) + "\n"
            + "Effective balance: " + numeric(intent.effectiveAvailableBalance()) + "\n"
            + "Reserved before order: " + numeric(intent.reservedBalance()) + "\n"
            + "Market: " + escape(textOrDefault(intent.marketName(), "n/a")) + "\n"
            + "Betfair bet id: " + escape(textOrDefault(intent.externalOrderId(), "n/a")) + "\n\n"
            + "Why this bet:\n"
            + TelegramMessageFormat.reasonLines(intent.reason()) + "\n\n"
            + "Status: accepted by exchange.";
    }

    private BetfairAutoBettingConfig autoBettingConfig(BetxConfig config, String exchange) {
        if (!"betfair".equals(exchange)) {
            return new BetfairAutoBettingConfig(null, null, null, null, null);
        }
        return config.exchanges().stream()
            .filter(candidate -> "betfair".equals(candidate.name()))
            .findFirst()
            .map(candidate -> candidate.betfair().autoBetting())
            .orElseGet(() -> config.betfair().autoBetting());
    }

    private boolean confirmationRequired(BetxConfig config) {
        return config.enabledExchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .anyMatch(exchange -> exchange.betfair().autoBetting().enabled()
                && exchange.betfair().autoBetting().requestConfirmation());
    }

    private String formatInitialMessage(BetIntent intent) {
        return "<b>BET CONFIRMATION</b>\n\n"
            + "<b>" + escape(intent.eventName()) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + TelegramMessageFormat.actionLine(intent.exchange()) + "\n\n"
            + "Stake cap: " + numeric(intent.maxStake()) + "\n"
            + "Market: " + escape(textOrDefault(intent.marketName(), "n/a")) + "\n\n"
            + "Why this signal:\n"
            + TelegramMessageFormat.reasonLines(intent.reason()) + "\n\n"
            + "Safety:\n"
            + "No bet is placed until you confirm and choose stake.\n\n"
            + "Confirm bet?";
    }

    private String formatStakeSelectionMessage(BetIntent intent, BigDecimal maxAllowed) {
        return "<b>CHOOSE STAKE</b>\n\n"
            + "<b>" + escape(intent.eventName()) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + TelegramMessageFormat.actionLine(intent.exchange()) + "\n\n"
            + "Balance available: " + numeric(intent.availableBalance()) + "\n"
            + "Max allowed: " + numeric(maxAllowed) + "\n\n"
            + "Choose stake:";
    }

    private String formatCancelledMessage(BetIntent intent) {
        return "<b>BET CANCELLED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + "Status: cancelled by user.";
    }

    private String formatExecutedMessage(BetIntent intent, BigDecimal amount) {
        return "<b>BET EXECUTED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + "Stake: " + numeric(amount) + "\n"
            + "Status: accepted.";
    }

    private String formatRejectedMessage(BetIntent intent, String message) {
        String stake = intent.selectedStake() == null ? "" : "Stake: " + numeric(intent.selectedStake()) + "\n";
        return "<b>BET REJECTED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + stake
            + "Status: " + escape(textOrDefault(message, "rejected"));
    }

    private Map<String, Object> confirmationKeyboard(String intentId) {
        return Map.of(
            "inline_keyboard",
            List.of(
                List.of(
                    button("Yes", "bet:" + intentId + ":yes"),
                    button("No", "bet:" + intentId + ":no")
                )
            )
        );
    }

    private Map<String, Object> stakeKeyboard(String intentId, BigDecimal maxAllowed) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();
        for (BigDecimal preset : STAKE_PRESETS) {
            if (preset.compareTo(maxAllowed) <= 0) {
                row.add(button(label(preset), "bet:" + intentId + ":stake:" + preset.toPlainString()));
                if (row.size() == 3) {
                    rows.add(List.copyOf(row));
                    row.clear();
                }
            }
        }
        if (maxAllowed.compareTo(BigDecimal.ZERO) > 0
            && STAKE_PRESETS.stream().noneMatch(preset -> preset.compareTo(maxAllowed) == 0)) {
            row.add(button(label(maxAllowed), "bet:" + intentId + ":stake:" + maxAllowed.toPlainString()));
        }
        row.add(button("Cancel", "bet:" + intentId + ":cancel"));
        rows.add(List.copyOf(row));
        return Map.of("inline_keyboard", rows);
    }

    private Map<String, Object> button(String text, String callbackData) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }

    private String label(BigDecimal value) {
        return numeric(value);
    }

    private String key(RunnerAnalysis analysis) {
        return key(analysis.exchange(), analysis.marketId(), analysis.selectionId());
    }

    private String key(String exchange, String marketId, long selectionId) {
        return exchange + "|" + marketId + "|" + selectionId;
    }

    private String marketKey(String exchange, String marketId) {
        return exchange + "|" + marketId;
    }

    private String textOrDefault(String value, String fallback) {
        return TelegramMessageFormat.textOrDefault(value, fallback);
    }

    private String numeric(BigDecimal value) {
        return TelegramMessageFormat.numeric(value);
    }

    private String escape(String value) {
        return TelegramMessageFormat.escape(value);
    }

    private void auditCreated(BetIntent intent) {
        logIntentEvent("bet_intent.created", BetxEventCategory.AUDIT, "created", intent)
            .field("stage", intent.stage())
            .emit();
        emit("BET INTENT CREATED | id=" + intent.id()
            + " | exchange=" + intent.exchange()
            + " | marketId=" + intent.marketId()
            + " | selectionId=" + intent.selectionId());
    }

    private void auditSkipped(String reason, String exchange, String marketId, long selectionId) {
        eventLogger.warn(BetxEventCategory.AUDIT, "bet_intent.skipped")
            .correlationId("sig-" + exchange + "-" + marketId + "-" + selectionId)
            .exchange(exchange)
            .marketId(marketId)
            .selectionId(selectionId)
            .strategy("value-football")
            .result("skipped")
            .field("reason", reason)
            .emit();
        emit("BET INTENT SKIPPED | reason=" + reason
            + " | exchange=" + exchange
            + " | marketId=" + marketId
            + " | selectionId=" + selectionId);
    }

    private void auditTelegramFailure(String action, RuntimeException exc) {
        String message = exc.getMessage();
        dependencyError("telegram", action, exc);
        emit("TELEGRAM BET SYNC WARNING | action=" + action
            + " | message=" + (message == null || message.isBlank() ? exc.getClass().getSimpleName() : message));
    }

    private BetxEventLogger.EventBuilder logIntentEvent(
        String event,
        BetxEventCategory category,
        String result,
        BetIntent intent
    ) {
        return eventLogger.info(category, event)
            .correlationId("intent-" + intent.id())
            .exchange(intent.exchange())
            .marketId(intent.marketId())
            .selectionId(intent.selectionId())
            .strategy("value-football")
            .executionMode(intent.source() == BetIntentSource.AUTOMATIC ? "automatic" : "telegram_confirmation")
            .result(result)
            .field("betIntentId", intent.id())
            .field("source", intent.source())
            .field("stage", intent.stage())
            .field("odds", intent.odds())
            .field("selectedStake", intent.selectedStake())
            .field("availableBalance", intent.availableBalance())
            .field("effectiveAvailableBalance", intent.effectiveAvailableBalance())
            .field("reservedBalance", intent.reservedBalance())
            .field("externalOrderId", intent.externalOrderId());
    }

    private String signalCorrelationId(BetSignal signal) {
        return "sig-" + signal.exchange() + "-" + signal.marketId() + "-" + signal.selectionId();
    }

    private void dependencyError(String dependency, String action, RuntimeException exc) {
        eventLogger.error(BetxEventCategory.ERROR, "dependency.error")
            .result("failed")
            .field("dependency", dependency)
            .field("action", action)
            .field("errorType", exc.getClass().getSimpleName())
            .field("message", safeMessage(exc))
            .emit();
    }

    private record RiskBlock(String reason, String message) {
    }

    private static final class AutomaticBettingExchangeState {
        private final int maxOpenPositions;
        private final int initialOpenPositions;
        private final BigDecimal availableBalance;
        private final Optional<RiskBlock> riskBlock;
        private int acceptedOrders;
        private boolean closed;

        private AutomaticBettingExchangeState(
            int maxOpenPositions,
            int initialOpenPositions,
            BigDecimal availableBalance,
            Optional<RiskBlock> riskBlock
        ) {
            this.maxOpenPositions = maxOpenPositions;
            this.initialOpenPositions = initialOpenPositions;
            this.availableBalance = availableBalance;
            this.riskBlock = riskBlock == null ? Optional.empty() : riskBlock;
        }

        private Optional<RiskBlock> riskBlock() {
            return riskBlock;
        }

        private BigDecimal availableBalance() {
            return availableBalance;
        }

        private int remainingOpenPositionCapacity() {
            return maxOpenPositions - initialOpenPositions - acceptedOrders;
        }

        private void recordAcceptedOrder() {
            acceptedOrders++;
        }

        private boolean closed() {
            return closed;
        }

        private void close() {
            closed = true;
        }
    }

    private static final class NoopMarketSnapshotRepository implements MarketSnapshotRepository {
        @Override
        public Optional<com.betx.domain.signal.ObservedMarketSnapshot> findLatest(
            String databasePath,
            String exchange,
            String marketId,
            long selectionId
        ) {
            return Optional.empty();
        }

        @Override
        public void save(String databasePath, com.betx.domain.signal.ObservedMarketSnapshot snapshot) {
        }
    }

    private static final class NoopSignalHistoryRepository implements SignalHistoryRepository {
        @Override
        public void saveDecision(String databasePath, SignalHistoryEntry entry) {
        }

        @Override
        public void linkIntent(String databasePath, SignalHistoryKey key, BetIntent intent) {
        }

        @Override
        public void updateOrderState(String databasePath, BetIntent intent) {
        }
    }

    private static final class NoopTelegramStateRepository implements TelegramStateRepository {
        @Override
        public long loadLastProcessedUpdateId(String databasePath) {
            return 0L;
        }

        @Override
        public void saveLastProcessedUpdateId(String databasePath, long updateId) {
        }
    }

    private record CallbackAction(ActionType type, String intentId, BigDecimal amount) {
        private static CallbackAction parse(String data) {
            if (data == null || !data.startsWith("bet:")) {
                return null;
            }
            String[] parts = data.split(":");
            if (parts.length < 3) {
                return null;
            }
            ActionType type = ActionType.from(parts[2]);
            if (type == null) {
                return null;
            }
            BigDecimal amount = null;
            if (type == ActionType.STAKE && parts.length >= 4) {
                try {
                    amount = new BigDecimal(parts[3]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return new CallbackAction(type, parts[1], amount);
        }
    }

    private enum ActionType {
        YES,
        NO,
        CANCEL,
        STAKE;

        private static ActionType from(String value) {
            if (value == null) {
                return null;
            }
            try {
                return ActionType.valueOf(value.strip().toUpperCase());
            } catch (IllegalArgumentException exc) {
                return null;
            }
        }
    }
}
