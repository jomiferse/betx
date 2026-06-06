package com.betx.application;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.application.port.out.TelegramBetIntentRepository;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetOrder;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TelegramBetConfirmationService {
    private static final Duration SELECTION_COOLDOWN = Duration.ofMinutes(30);
    private static final List<TelegramBetIntentStage> OPEN_POSITION_STAGES = List.of(
        TelegramBetIntentStage.AWAITING_CONFIRMATION,
        TelegramBetIntentStage.AWAITING_STAKE,
        TelegramBetIntentStage.EXECUTED
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
    private final TelegramBetIntentRepository intentRepository;
    private final ExchangeAccountGateway accountGateway;
    private final BetExecutionGateway executionGateway;
    private final Clock clock;

    @Autowired
    public TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        TelegramBetIntentRepository intentRepository,
        ExchangeAccountGateway accountGateway,
        @Qualifier("betfairBetExecutionGateway") BetExecutionGateway executionGateway
    ) {
        this(configRepository, telegramConnectionService, telegramGateway, intentRepository, accountGateway, executionGateway, Clock.systemUTC());
    }

    TelegramBetConfirmationService(
        BetxConfigRepository configRepository,
        TelegramConnectionService telegramConnectionService,
        TelegramBotGateway telegramGateway,
        TelegramBetIntentRepository intentRepository,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway,
        Clock clock
    ) {
        this.configRepository = configRepository;
        this.telegramConnectionService = telegramConnectionService;
        this.telegramGateway = telegramGateway;
        this.intentRepository = intentRepository;
        this.accountGateway = accountGateway;
        this.executionGateway = executionGateway;
        this.clock = clock;
    }

    public void sync(ConfigPath configPath, DryRunSignalsResult result) {
        BetxConfig config = configRepository.load(configPath);
        if (!config.telegram().enabled() || !"live".equals(config.app().mode())) {
            return;
        }

        Optional<TelegramConnectionContext> context = telegramConnectionService.connectionContext(configPath);
        if (context.isEmpty()) {
            return;
        }

        processCallbacks(configPath, config, context.get());
        offerBetConfirmations(configPath, config, result);
    }

    private void offerBetConfirmations(ConfigPath configPath, BetxConfig config, DryRunSignalsResult result) {
        Map<String, RunnerAnalysis> analysesByKey = result.runnerAnalyses().stream()
            .filter(analysis -> analysis.recommendation() == RecommendationType.BET)
            .collect(Collectors.toMap(this::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));

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
            if (intentRepository.countByStages(config.storage().path(), OPEN_POSITION_STAGES) >= config.risk().maxOpenPositions()) {
                auditSkipped("max_open_positions", signal.exchange(), signal.marketId(), signal.selectionId());
                continue;
            }

            TelegramBetIntent intent = new TelegramBetIntent(
                UUID.randomUUID().toString(),
                signal.exchange(),
                signal.marketId(),
                signal.selectionId(),
                analysis.eventName(),
                analysis.marketName(),
                analysis.displayRunner(),
                signal.reason(),
                signal.odds(),
                config.risk().maxStake(),
                null,
                null,
                null,
                TelegramBetIntentStage.AWAITING_CONFIRMATION,
                now,
                now
            );
            intentRepository.save(config.storage().path(), intent);
            auditCreated(intent);
            safeTelegramSend(
                configPath,
                formatInitialMessage(intent),
                TelegramParseMode.HTML,
                confirmationKeyboard(intent.id())
            );
        }
    }

    private void processCallbacks(ConfigPath configPath, BetxConfig config, TelegramConnectionContext context) {
        long lastUpdateId = intentRepository.loadLastProcessedUpdateId(config.storage().path());
        List<TelegramUpdate> updates = telegramGateway.getUpdates(context.token(), lastUpdateId == 0L ? null : lastUpdateId + 1L, 0);
        long maxUpdateId = lastUpdateId;

        for (TelegramUpdate update : updates) {
            maxUpdateId = Math.max(maxUpdateId, update.updateId());
            if (update.hasCallbackQuery()) {
                try {
                    handleCallback(configPath, config, update);
                } catch (RuntimeException exc) {
                    auditTelegramFailure("callback_processing", exc);
                }
            }
        }

        if (maxUpdateId > lastUpdateId) {
            intentRepository.saveLastProcessedUpdateId(config.storage().path(), maxUpdateId);
        }
    }

    private void handleCallback(ConfigPath configPath, BetxConfig config, TelegramUpdate update) {
        CallbackAction action = CallbackAction.parse(update.callbackData());
        if (action == null) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Unsupported action.", false);
            return;
        }

        Optional<TelegramBetIntent> intentOptional = intentRepository.findById(config.storage().path(), action.intentId());
        if (intentOptional.isEmpty()) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Intent not found.", false);
            return;
        }

        TelegramBetIntent intent = intentOptional.get();
        switch (action.type()) {
            case YES -> handleYes(configPath, config, update, intent);
            case NO, CANCEL -> handleCancel(configPath, config, update, intent);
            case STAKE -> handleStake(configPath, config, update, intent, action.amount());
        }
    }

    private void handleYes(ConfigPath configPath, BetxConfig config, TelegramUpdate update, TelegramBetIntent intent) {
        if (!intent.stage().isActive() || intent.stage() != TelegramBetIntentStage.AWAITING_CONFIRMATION) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Already processed.", false);
            return;
        }

        Optional<BigDecimal> availableBalance = accountGateway.availableBalance(config, intent.exchange());
        if (availableBalance.isEmpty() || availableBalance.get().compareTo(BigDecimal.ZERO) <= 0) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Balance unavailable.", true);
            return;
        }

        TelegramBetIntent updated = intent.withStageAt(
            TelegramBetIntentStage.AWAITING_STAKE,
            availableBalance.get(),
            null,
            "Stake selection requested.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        System.out.println("TELEGRAM BET STAKE REQUESTED | id=" + updated.id()
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

    private void handleCancel(ConfigPath configPath, BetxConfig config, TelegramUpdate update, TelegramBetIntent intent) {
        TelegramBetIntent updated = intent.withStageAt(
            TelegramBetIntentStage.CANCELLED,
            intent.availableBalance(),
            intent.selectedStake(),
            "Cancelled by Telegram user.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        System.out.println("TELEGRAM BET INTENT CANCELLED | id=" + updated.id()
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

    private void handleStake(ConfigPath configPath, BetxConfig config, TelegramUpdate update, TelegramBetIntent intent, BigDecimal amount) {
        if (intent.stage() != TelegramBetIntentStage.AWAITING_STAKE) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Stake already resolved.", false);
            return;
        }

        BigDecimal maxAllowed = maxAllowedStake(config, intent);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxAllowed) > 0) {
            safeTelegramAnswer(configPath, update.callbackQueryId(), "Amount not allowed.", true);
            return;
        }

        if (!config.risk().liveBettingEnabled()) {
            String message = "Live betting is disabled.";
            TelegramBetIntent updated = intent.withStageAt(
                TelegramBetIntentStage.FAILED,
                intent.availableBalance(),
                amount,
                message,
                Instant.now(clock)
            );
            intentRepository.update(config.storage().path(), updated);
            System.out.println("TELEGRAM BET EXECUTION BLOCKED | reason=live_betting_disabled"
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

        if (intentRepository.countByStages(config.storage().path(), List.of(TelegramBetIntentStage.EXECUTED))
            >= config.risk().maxOpenPositions()) {
            blockExecution(configPath, config, update, intent, amount, "max_open_positions", "Open position limit reached.");
            return;
        }
        if (dailyRiskLimitExceeded(config, amount)) {
            blockExecution(configPath, config, update, intent, amount, "max_daily_loss", "Daily risk limit exceeded.");
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
        var result = executionGateway.execute(configPath, order);
        TelegramBetIntent updated = intent.withStageAt(
            result.accepted() ? TelegramBetIntentStage.EXECUTED : TelegramBetIntentStage.FAILED,
            intent.availableBalance(),
            amount,
            result.message(),
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        if (result.accepted()) {
            System.out.println("TELEGRAM BET ORDER ACCEPTED | id=" + updated.id()
                + " | stake=" + amount
                + " | exchange=" + updated.exchange()
                + " | marketId=" + updated.marketId()
                + " | selectionId=" + updated.selectionId());
        } else {
            System.out.println("TELEGRAM BET ORDER REJECTED | id=" + updated.id()
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
        TelegramBetIntent intent,
        BigDecimal amount,
        String reason,
        String message
    ) {
        TelegramBetIntent updated = intent.withStageAt(
            TelegramBetIntentStage.FAILED,
            intent.availableBalance(),
            amount,
            message,
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), updated);
        System.out.println("TELEGRAM BET EXECUTION BLOCKED | reason=" + reason
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

    private void safeTelegramSend(
        ConfigPath configPath,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        try {
            telegramConnectionService.sendMessageIfConnected(configPath, text, parseMode, replyMarkup);
        } catch (RuntimeException exc) {
            auditTelegramFailure("send_message", exc);
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

    private boolean dailyRiskLimitExceeded(BetxConfig config, BigDecimal amount) {
        BigDecimal alreadyCommitted = intentRepository.sumSelectedStakeByStageSince(
            config.storage().path(),
            TelegramBetIntentStage.EXECUTED,
            todayStart()
        );
        return alreadyCommitted.add(amount).compareTo(config.risk().maxDailyLoss()) > 0;
    }

    private Instant todayStart() {
        return Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private BigDecimal maxAllowedStake(BetxConfig config, TelegramBetIntent intent) {
        return maxAllowedStake(config, intent.availableBalance(), intent.maxStake());
    }

    private BigDecimal maxAllowedStake(BetxConfig config, BigDecimal availableBalance, BigDecimal configuredMax) {
        BigDecimal maxStake = configuredMax == null ? config.risk().maxStake() : configuredMax;
        if (availableBalance == null) {
            return maxStake;
        }
        return maxStake.min(availableBalance).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatInitialMessage(TelegramBetIntent intent) {
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

    private String formatStakeSelectionMessage(TelegramBetIntent intent, BigDecimal maxAllowed) {
        return "<b>CHOOSE STAKE</b>\n\n"
            + "<b>" + escape(intent.eventName()) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + TelegramMessageFormat.actionLine(intent.exchange()) + "\n\n"
            + "Balance available: " + numeric(intent.availableBalance()) + "\n"
            + "Max allowed: " + numeric(maxAllowed) + "\n\n"
            + "Choose stake:";
    }

    private String formatCancelledMessage(TelegramBetIntent intent) {
        return "<b>BET CANCELLED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + "Status: cancelled by user.";
    }

    private String formatExecutedMessage(TelegramBetIntent intent, BigDecimal amount) {
        return "<b>BET EXECUTED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + TelegramMessageFormat.selectionLine(intent.displayRunner(), intent.odds()) + "\n"
            + "Stake: " + numeric(amount) + "\n"
            + "Status: accepted.";
    }

    private String formatRejectedMessage(TelegramBetIntent intent, String message) {
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

    private String textOrDefault(String value, String fallback) {
        return TelegramMessageFormat.textOrDefault(value, fallback);
    }

    private String numeric(BigDecimal value) {
        return TelegramMessageFormat.numeric(value);
    }

    private String escape(String value) {
        return TelegramMessageFormat.escape(value);
    }

    private void auditCreated(TelegramBetIntent intent) {
        System.out.println("TELEGRAM BET INTENT CREATED | id=" + intent.id()
            + " | exchange=" + intent.exchange()
            + " | marketId=" + intent.marketId()
            + " | selectionId=" + intent.selectionId());
    }

    private void auditSkipped(String reason, String exchange, String marketId, long selectionId) {
        System.out.println("TELEGRAM BET INTENT SKIPPED | reason=" + reason
            + " | exchange=" + exchange
            + " | marketId=" + marketId
            + " | selectionId=" + selectionId);
    }

    private void auditTelegramFailure(String action, RuntimeException exc) {
        String message = exc.getMessage();
        System.out.println("TELEGRAM BET SYNC WARNING | action=" + action
            + " | message=" + (message == null || message.isBlank() ? exc.getClass().getSimpleName() : message));
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
