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
import java.time.Instant;
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
        if (!config.telegram().enabled() || !config.risk().liveBettingEnabled() || !"live".equals(config.app().mode())) {
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
                TelegramBetIntentStage.AWAITING_CONFIRMATION,
                Instant.now(clock),
                Instant.now(clock)
            );
            intentRepository.save(config.storage().path(), intent);
            telegramConnectionService.sendMessageIfConnected(
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
                handleCallback(configPath, config, update);
            }
        }

        if (maxUpdateId > lastUpdateId) {
            intentRepository.saveLastProcessedUpdateId(config.storage().path(), maxUpdateId);
        }
    }

    private void handleCallback(ConfigPath configPath, BetxConfig config, TelegramUpdate update) {
        CallbackAction action = CallbackAction.parse(update.callbackData());
        if (action == null) {
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Unsupported action.", false);
            return;
        }

        Optional<TelegramBetIntent> intentOptional = intentRepository.findById(config.storage().path(), action.intentId());
        if (intentOptional.isEmpty()) {
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Intent not found.", false);
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
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Already processed.", false);
            return;
        }

        Optional<BigDecimal> availableBalance = accountGateway.availableBalance(config, intent.exchange());
        if (availableBalance.isEmpty() || availableBalance.get().compareTo(BigDecimal.ZERO) <= 0) {
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Balance unavailable.", true);
            return;
        }

        TelegramBetIntent updated = intent.withStage(TelegramBetIntentStage.AWAITING_STAKE, availableBalance.get(), null);
        intentRepository.update(config.storage().path(), updated);

        BigDecimal maxAllowed = maxAllowedStake(config, updated);
        telegramConnectionService.editMessageIfConnected(
            configPath,
            update.messageId(),
            formatStakeSelectionMessage(updated, maxAllowed),
            TelegramParseMode.HTML,
            stakeKeyboard(updated.id(), maxAllowed)
        );
        telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Choose a stake.", false);
    }

    private void handleCancel(ConfigPath configPath, BetxConfig config, TelegramUpdate update, TelegramBetIntent intent) {
        TelegramBetIntent updated = intent.withStage(TelegramBetIntentStage.CANCELLED, intent.availableBalance(), intent.selectedStake());
        intentRepository.update(config.storage().path(), updated);
        telegramConnectionService.editMessageIfConnected(
            configPath,
            update.messageId(),
            formatCancelledMessage(updated),
            TelegramParseMode.HTML,
            Map.of("inline_keyboard", List.of())
        );
        telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Cancelled.", false);
    }

    private void handleStake(ConfigPath configPath, BetxConfig config, TelegramUpdate update, TelegramBetIntent intent, BigDecimal amount) {
        if (intent.stage() != TelegramBetIntentStage.AWAITING_STAKE) {
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Stake already resolved.", false);
            return;
        }

        BigDecimal maxAllowed = maxAllowedStake(config, intent);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxAllowed) > 0) {
            telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), "Amount not allowed.", true);
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
        TelegramBetIntent updated = intent.withStage(
            result.accepted() ? TelegramBetIntentStage.EXECUTED : TelegramBetIntentStage.FAILED,
            intent.availableBalance(),
            amount
        );
        intentRepository.update(config.storage().path(), updated);
        telegramConnectionService.editMessageIfConnected(
            configPath,
            update.messageId(),
            result.accepted() ? formatExecutedMessage(updated, amount) : formatRejectedMessage(updated, result.message()),
            TelegramParseMode.HTML,
            Map.of("inline_keyboard", List.of())
        );
        telegramConnectionService.answerCallbackIfConnected(configPath, update.callbackQueryId(), result.message(), !result.accepted());
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
            + "Runner: " + escape(displayRunner(intent.displayRunner())) + "\n"
            + "Market: " + escape(textOrDefault(intent.marketName(), "n/a")) + "\n"
            + "Exchange: " + escape(textOrDefault(intent.exchange(), "n/a")) + "\n"
            + "Odds: " + numeric(intent.odds()) + "\n"
            + "Stake cap: " + numeric(intent.maxStake()) + "\n"
            + "Reason: " + escape(textOrDefault(intent.reason(), "n/a")) + "\n\n"
            + "Confirm bet?";
    }

    private String formatStakeSelectionMessage(TelegramBetIntent intent, BigDecimal maxAllowed) {
        return "<b>BET CONFIRMATION</b>\n\n"
            + "<b>" + escape(intent.eventName()) + "</b>\n"
            + "Runner: " + escape(displayRunner(intent.displayRunner())) + "\n"
            + "Market: " + escape(textOrDefault(intent.marketName(), "n/a")) + "\n"
            + "Exchange: " + escape(textOrDefault(intent.exchange(), "n/a")) + "\n"
            + "Odds: " + numeric(intent.odds()) + "\n"
            + "Available balance: " + numeric(intent.availableBalance()) + "\n"
            + "Max allowed: " + numeric(maxAllowed) + "\n\n"
            + "Choose a stake.";
    }

    private String formatCancelledMessage(TelegramBetIntent intent) {
        return "<b>BET CANCELLED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + "Runner: " + escape(displayRunner(intent.displayRunner())) + "\n"
            + "Status: cancelled.";
    }

    private String formatExecutedMessage(TelegramBetIntent intent, BigDecimal amount) {
        return "<b>BET EXECUTED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + "Runner: " + escape(displayRunner(intent.displayRunner())) + "\n"
            + "Stake: " + numeric(amount) + "\n"
            + "Status: accepted.";
    }

    private String formatRejectedMessage(TelegramBetIntent intent, String message) {
        return "<b>BET REJECTED</b>\n\n"
            + "<b>" + escape(textOrDefault(intent.eventName(), "unknown event")) + "</b>\n"
            + "Runner: " + escape(displayRunner(intent.displayRunner())) + "\n"
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

    private String displayRunner(String runner) {
        return "The Draw".equalsIgnoreCase(runner) ? "Draw" : runner;
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String numeric(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String value) {
        return textOrDefault(value, "n/a")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
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
