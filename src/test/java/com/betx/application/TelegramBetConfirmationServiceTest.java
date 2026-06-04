package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.application.port.out.TelegramBetIntentRepository;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
import com.betx.domain.telegram.TelegramConnectionContext;
import com.betx.domain.telegram.TelegramUpdate;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramBetConfirmationServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void offersConfirmationButtonsForNewBetSignals() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));

        assertThat(telegram.sentMessages()).hasSize(1);
        assertThat(telegram.sentMessages().getFirst().text()).contains("Confirm bet").contains("Team A");
        assertThat(telegram.sentMessages().getFirst().replyMarkup()).isNotNull();
        assertThat(intents.saved()).hasSize(1);
        assertThat(intents.saved().getFirst().stage()).isEqualTo(TelegramBetIntentStage.AWAITING_CONFIRMATION);
    }

    @Test
    void yesMovesIntentToStakeSelectionAndShowsAllowedAmounts() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(telegram.editedMessages()).singleElement().satisfies(edit -> {
            assertThat(edit.text()).contains("Choose a stake").contains("Available balance: 12.50");
            assertThat(edit.replyMarkup()).isNotNull();
        });
        assertThat(intents.updated().getFirst().stage()).isEqualTo(TelegramBetIntentStage.AWAITING_STAKE);
        assertThat(intents.updated().getFirst().availableBalance()).isEqualByComparingTo("12.50");
    }

    @Test
    void noCancelsPendingIntent() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            new RecordingExecutionGateway()
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "no", 77));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(telegram.editedMessages()).singleElement().satisfies(edit ->
            assertThat(edit.text()).contains("BET CANCELLED")
        );
        assertThat(intents.updated().getFirst().stage()).isEqualTo(TelegramBetIntentStage.CANCELLED);
    }

    @Test
    void stakeSelectionExecutesTheBet() {
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        RecordingIntentRepository intents = new RecordingIntentRepository();
        RecordingExecutionGateway executionGateway = new RecordingExecutionGateway();
        TelegramBetConfirmationService service = service(
            telegram,
            gateway,
            intents,
            new StaticAccountGateway(BigDecimal.valueOf(12.5)),
            executionGateway
        );

        service.sync(CONFIG_PATH, resultOf(signal("betfair", "1.1", 42L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5)), analysis("Team A")));
        String intentId = intents.saved().getFirst().id();
        gateway.addUpdate(newCallbackUpdate(1L, intentId, "yes", 77));
        service.sync(CONFIG_PATH, resultOf());

        telegram.clear();
        gateway.addUpdate(newCallbackUpdate(2L, intentId, "stake", 77, BigDecimal.valueOf(5)));

        service.sync(CONFIG_PATH, resultOf());

        assertThat(executionGateway.orders()).singleElement().satisfies(order -> {
            assertThat(order.exchange()).isEqualTo("betfair");
            assertThat(order.marketId()).isEqualTo("1.1");
            assertThat(order.selectionId()).isEqualTo(42L);
            assertThat(order.stake()).isEqualByComparingTo("5");
        });
        assertThat(intents.updated().getLast().stage()).isEqualTo(TelegramBetIntentStage.EXECUTED);
    }

    private TelegramBetConfirmationService service(
        RecordingTelegramConnectionService telegram,
        RecordingTelegramGateway gateway,
        RecordingIntentRepository intents,
        ExchangeAccountGateway accountGateway,
        BetExecutionGateway executionGateway
    ) {
        return new TelegramBetConfirmationService(
            new StaticConfigRepository(BetxConfig.defaults().withMode("live").withLiveBettingEnabled(true)),
            telegram,
            gateway,
            intents,
            accountGateway,
            executionGateway
        );
    }

    private DryRunSignalsResult resultOf(BetSignal signal, RunnerAnalysis analysis) {
        return new DryRunSignalsResult(
            List.of(signal),
            List.of(),
            false,
            0,
            0,
            List.of(),
            List.of(analysis),
            0,
            0,
            0,
            0
        );
    }

    private DryRunSignalsResult resultOf() {
        return new DryRunSignalsResult(List.of(), List.of(), false);
    }

    private BetSignal signal(String exchange, String marketId, long selectionId, BigDecimal odds, BigDecimal stake) {
        return new BetSignal(exchange, marketId, selectionId, BetSide.BACK, odds, stake, "liquidity_ok", "live");
    }

    private RunnerAnalysis analysis(String runnerName) {
        return new RunnerAnalysis(
            "betfair",
            "1.1",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            runnerName,
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(2.6),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200),
            RecommendationType.BET,
            "liquidity ok"
        );
    }

    private TelegramUpdate newCallbackUpdate(long updateId, String intentId, String action, Integer messageId) {
        return newCallbackUpdate(updateId, intentId, action, messageId, null);
    }

    private TelegramUpdate newCallbackUpdate(long updateId, String intentId, String action, Integer messageId, BigDecimal amount) {
        String callbackData = amount == null
            ? "bet:" + intentId + ":" + action
            : "bet:" + intentId + ":stake:" + amount.toPlainString();
        return new TelegramUpdate(
            updateId,
            "12345",
            null,
            "user",
            "Jose",
            "callback-" + updateId,
            callbackData,
            messageId
        );
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private static final class RecordingTelegramConnectionService extends TelegramConnectionService {
        private final List<SentMessage> sentMessages = new ArrayList<>();
        private final List<EditedMessage> editedMessages = new ArrayList<>();
        private final List<String> callbackAnswers = new ArrayList<>();

        private RecordingTelegramConnectionService() {
            super(null, null, null);
        }

        @Override
        public Optional<TelegramConnectionContext> connectionContext(ConfigPath configPath) {
            return Optional.of(new TelegramConnectionContext("token", "12345"));
        }

        @Override
        public boolean sendMessageIfConnected(
            ConfigPath configPath,
            String text,
            com.betx.application.port.out.TelegramParseMode parseMode,
            Map<String, Object> replyMarkup
        ) {
            sentMessages.add(new SentMessage(text, replyMarkup));
            return true;
        }

        @Override
        public boolean editMessageIfConnected(
            ConfigPath configPath,
            Integer messageId,
            String text,
            com.betx.application.port.out.TelegramParseMode parseMode,
            Map<String, Object> replyMarkup
        ) {
            editedMessages.add(new EditedMessage(messageId, text, replyMarkup));
            return true;
        }

        @Override
        public boolean answerCallbackIfConnected(ConfigPath configPath, String callbackQueryId, String text, boolean showAlert) {
            callbackAnswers.add(callbackQueryId + ":" + text + ":" + showAlert);
            return true;
        }

        void clear() {
            sentMessages.clear();
            editedMessages.clear();
            callbackAnswers.clear();
        }

        List<SentMessage> sentMessages() {
            return sentMessages;
        }

        List<EditedMessage> editedMessages() {
            return editedMessages;
        }
    }

    private static final class RecordingTelegramGateway implements TelegramBotGateway {
        private final List<TelegramUpdate> updates = new ArrayList<>();

        @Override
        public String getBotUsername(String token) {
            return "bot";
        }

        @Override
        public List<TelegramUpdate> getUpdates(String token, Long offset, int timeoutSeconds) {
            return updates.stream()
                .filter(update -> offset == null || update.updateId() >= offset)
                .toList();
        }

        @Override
        public void sendMessage(String token, String chatId, String text) {
        }

        void addUpdate(TelegramUpdate update) {
            updates.add(update);
        }
    }

    private record SentMessage(String text, Map<String, Object> replyMarkup) {
    }

    private record EditedMessage(Integer messageId, String text, Map<String, Object> replyMarkup) {
    }

    private static final class RecordingIntentRepository implements TelegramBetIntentRepository {
        private final List<TelegramBetIntent> saved = new ArrayList<>();
        private final List<TelegramBetIntent> updated = new ArrayList<>();

        @Override
        public Optional<TelegramBetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
            return saved.stream()
                .filter(intent -> intent.exchange().equals(exchange)
                    && intent.marketId().equals(marketId)
                    && intent.selectionId() == selectionId
                    && intent.stage().isActive())
                .findFirst();
        }

        @Override
        public Optional<TelegramBetIntent> findById(String databasePath, String id) {
            return saved.stream().filter(intent -> intent.id().equals(id)).findFirst();
        }

        @Override
        public void save(String databasePath, TelegramBetIntent intent) {
            saved.add(intent);
        }

        @Override
        public void update(String databasePath, TelegramBetIntent intent) {
            updated.add(intent);
            for (int index = 0; index < saved.size(); index++) {
                if (saved.get(index).id().equals(intent.id())) {
                    saved.set(index, intent);
                    return;
                }
            }
            saved.add(intent);
        }

        @Override
        public long loadLastProcessedUpdateId(String databasePath) {
            return 0L;
        }

        @Override
        public void saveLastProcessedUpdateId(String databasePath, long updateId) {
        }

        List<TelegramBetIntent> saved() {
            return saved;
        }

        List<TelegramBetIntent> updated() {
            return updated;
        }
    }

    private static final class StaticAccountGateway implements ExchangeAccountGateway {
        private final BigDecimal balance;

        private StaticAccountGateway(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public Optional<BigDecimal> availableBalance(BetxConfig config, String exchange) {
            return Optional.of(balance);
        }
    }

    private static final class RecordingExecutionGateway implements BetExecutionGateway {
        private final List<com.betx.domain.order.BetOrder> orders = new ArrayList<>();

        @Override
        public com.betx.domain.order.BetExecutionResult execute(com.betx.domain.order.BetOrder order) {
            orders.add(order);
            return new com.betx.domain.order.BetExecutionResult(true, "accepted");
        }

        List<com.betx.domain.order.BetOrder> orders() {
            return orders;
        }
    }
}
