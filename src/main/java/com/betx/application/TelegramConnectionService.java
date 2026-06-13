package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.EnvironmentProvider;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.application.port.out.TelegramStateRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.telegram.TelegramConnectionContext;
import com.betx.domain.telegram.TelegramConnectionResult;
import com.betx.domain.telegram.TelegramUpdate;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TelegramConnectionService {
    public static final String TEST_MESSAGE = "BetX test message ✅ Your Telegram integration is working.";
    public static final String CONFIRMATION_MESSAGE = "BetX Telegram alerts connected successfully ✅";

    private final BetxConfigRepository configRepository;
    private final EnvironmentProvider environment;
    private final TelegramBotGateway telegramGateway;
    private final TelegramStateRepository telegramStateRepository;
    private final Clock clock;
    private final Supplier<String> linkCodeFactory;

    @Autowired
    public TelegramConnectionService(
        BetxConfigRepository configRepository,
        EnvironmentProvider environment,
        TelegramBotGateway telegramGateway,
        TelegramStateRepository telegramStateRepository
    ) {
        this(
            configRepository,
            environment,
            telegramGateway,
            telegramStateRepository,
            Clock.systemUTC(),
            TelegramConnectionService::secureLinkCode
        );
    }

    public TelegramConnectionService(
        BetxConfigRepository configRepository,
        EnvironmentProvider environment,
        TelegramBotGateway telegramGateway
    ) {
        this(
            configRepository,
            environment,
            telegramGateway,
            new NoopTelegramStateRepository(),
            Clock.systemUTC(),
            TelegramConnectionService::secureLinkCode
        );
    }

    public TelegramConnectionService(
        BetxConfigRepository configRepository,
        EnvironmentProvider environment,
        TelegramBotGateway telegramGateway,
        Clock clock
    ) {
        this(
            configRepository,
            environment,
            telegramGateway,
            new NoopTelegramStateRepository(),
            clock,
            TelegramConnectionService::secureLinkCode
        );
    }

    public TelegramConnectionService(
        BetxConfigRepository configRepository,
        EnvironmentProvider environment,
        TelegramBotGateway telegramGateway,
        TelegramStateRepository telegramStateRepository,
        Clock clock
    ) {
        this(configRepository, environment, telegramGateway, telegramStateRepository, clock, TelegramConnectionService::secureLinkCode);
    }

    public TelegramConnectionService(
        BetxConfigRepository configRepository,
        EnvironmentProvider environment,
        TelegramBotGateway telegramGateway,
        TelegramStateRepository telegramStateRepository,
        Clock clock,
        Supplier<String> linkCodeFactory
    ) {
        this.configRepository = configRepository;
        this.environment = environment;
        this.telegramGateway = telegramGateway;
        this.telegramStateRepository = telegramStateRepository == null ? new NoopTelegramStateRepository() : telegramStateRepository;
        this.clock = clock;
        this.linkCodeFactory = linkCodeFactory;
    }

    public String status(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        if (!config.telegram().enabled()) {
            return "Telegram is disabled in betx.yml.";
        }
        String chatId = telegramChatId(config);
        if (chatId != null && !chatId.isBlank()) {
            String connectedAt = config.telegram().connectedAt();
            return connectedAt == null
                ? "Telegram is connected."
                : "Telegram is connected.\nConnected at: " + connectedAt;
        }
        return "Telegram is not connected.\nConnect it with:\n  betx telegram connect";
    }

    public boolean isConnected(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        String chatId = telegramChatId(config);
        return chatId != null && !chatId.isBlank();
    }

    public boolean isEnabled(ConfigPath configPath) {
        return configRepository.load(configPath).telegram().enabled();
    }

    public TelegramConnectionResult connect(
        ConfigPath configPath,
        long timeoutSeconds,
        Supplier<String> tokenPrompt,
        Consumer<String> deepLinkConsumer
    ) {
        BetxConfig config = configRepository.load(configPath);
        if (!config.telegram().enabled()) {
            throw new IllegalStateException("Telegram is disabled in betx.yml.");
        }

        String token = resolveToken(config, tokenPrompt);
        String botUsername = resolveBotUsername(config, token);
        String linkCode = linkCodeFactory.get();
        String deepLink = buildDeepLink(botUsername, linkCode);
        String databasePath = config.storage().path();

        configRepository.saveTelegramFields(configPath, Map.of("bot_token", token, "bot_username", botUsername, "pending_link_code", linkCode));
        deepLinkConsumer.accept(deepLink);

        long deadline = System.currentTimeMillis() + Math.max(timeoutSeconds, 0L) * 1000L;
        long lastProcessedUpdateId = telegramStateRepository.loadLastProcessedUpdateId(databasePath);
        Long offset = lastProcessedUpdateId == 0L ? null : lastProcessedUpdateId + 1L;
        do {
            var updates = telegramGateway.getUpdates(token, offset, 10);
            long acknowledgedUpdateId = lastProcessedUpdateId;
            for (TelegramUpdate update : updates) {
                if (linkCode.equals(update.startPayload())) {
                    telegramStateRepository.saveLastProcessedUpdateId(databasePath, update.updateId());
                    configRepository.saveTelegramFields(configPath, connectedFields(update));
                    telegramGateway.sendMessage(token, update.chatId(), CONFIRMATION_MESSAGE);
                    return new TelegramConnectionResult(true, deepLink, update.chatId());
                }
                acknowledgedUpdateId = Math.max(acknowledgedUpdateId, update.updateId());
            }
            if (acknowledgedUpdateId > lastProcessedUpdateId) {
                lastProcessedUpdateId = acknowledgedUpdateId;
                telegramStateRepository.saveLastProcessedUpdateId(databasePath, lastProcessedUpdateId);
                offset = lastProcessedUpdateId + 1L;
            }
            if (timeoutSeconds > 0) {
                sleepBriefly();
            }
        } while (System.currentTimeMillis() < deadline);

        return new TelegramConnectionResult(false, deepLink, null);
    }

    public void sendTestMessage(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        if (!config.telegram().enabled()) {
            throw new IllegalStateException("Telegram is disabled in betx.yml.");
        }
        String token = resolveToken(config, () -> null);
        String chatId = telegramChatId(config);
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Telegram is not connected.");
        }
        telegramGateway.sendMessage(token, chatId, TEST_MESSAGE);
    }

    public boolean sendMessageIfConnected(ConfigPath configPath, String text) {
        return sendMessageIfConnected(configPath, text, null);
    }

    public boolean sendMessageIfConnected(ConfigPath configPath, String text, TelegramParseMode parseMode) {
        return sendMessageIfConnected(configPath, text, parseMode, null);
    }

    public boolean sendMessageIfConnected(
        ConfigPath configPath,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        return connectionContext(configPath)
            .map(context -> {
                if (parseMode == null) {
                    if (replyMarkup == null) {
                        telegramGateway.sendMessage(context.token(), context.chatId(), text);
                    } else {
                        telegramGateway.sendMessage(context.token(), context.chatId(), text, null, replyMarkup);
                    }
                } else if (replyMarkup == null) {
                    telegramGateway.sendMessage(context.token(), context.chatId(), text, parseMode);
                } else {
                    telegramGateway.sendMessage(context.token(), context.chatId(), text, parseMode, replyMarkup);
                }
                return true;
            })
            .orElse(false);
    }

    public boolean editMessageIfConnected(
        ConfigPath configPath,
        Integer messageId,
        String text,
        TelegramParseMode parseMode,
        Map<String, Object> replyMarkup
    ) {
        return connectionContext(configPath)
            .map(context -> {
                telegramGateway.editMessageText(context.token(), context.chatId(), messageId, text, parseMode, replyMarkup);
                return true;
            })
            .orElse(false);
    }

    public boolean answerCallbackIfConnected(
        ConfigPath configPath,
        String callbackQueryId,
        String text,
        boolean showAlert
    ) {
        return connectionContext(configPath)
            .map(context -> {
                telegramGateway.answerCallbackQuery(context.token(), callbackQueryId, text, showAlert);
                return true;
            })
            .orElse(false);
    }

    public Optional<TelegramConnectionContext> connectionContext(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        if (!config.telegram().enabled()) {
            return Optional.empty();
        }

        String token = optionalToken(config);
        String chatId = telegramChatId(config);
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new TelegramConnectionContext(token, chatId));
    }

    public String buildDeepLink(String botUsername, String linkCode) {
        return "https://t.me/" + botUsername + "?start=" + linkCode;
    }

    private String resolveToken(BetxConfig config, Supplier<String> tokenPrompt) {
        String token = telegramBotToken(config);
        if ((token == null || token.isBlank()) && tokenPrompt != null) {
            token = tokenPrompt.get();
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "Telegram bot token is missing. Create a bot with BotFather and add TELEGRAM_BOT_TOKEN to your environment or betx.yml."
            );
        }
        return token.strip();
    }

    private String resolveBotUsername(BetxConfig config, String token) {
        if (config.telegram().botUsername() != null) {
            return config.telegram().botUsername();
        }
        return telegramGateway.getBotUsername(token);
    }

    private String telegramBotToken(BetxConfig config) {
        if (config.telegram().botToken() != null) {
            return config.telegram().botToken();
        }
        return environment.get(config.telegram().botTokenEnv());
    }

    private String optionalToken(BetxConfig config) {
        if (config.telegram().botToken() != null) {
            return config.telegram().botToken();
        }
        return environment.get(config.telegram().botTokenEnv());
    }

    private String telegramChatId(BetxConfig config) {
        if (config.telegram().chatId() != null) {
            return config.telegram().chatId();
        }
        return environment.get(config.telegram().chatIdEnv());
    }

    private Map<String, Object> connectedFields(TelegramUpdate update) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chat_id", update.chatId());
        fields.put("connected_at", Instant.now(clock).toString());
        fields.put("username", update.username());
        fields.put("first_name", update.firstName());
        fields.put("pending_link_code", null);
        return fields;
    }

    private static String secureLinkCode() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram connection interrupted.", exc);
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
}
