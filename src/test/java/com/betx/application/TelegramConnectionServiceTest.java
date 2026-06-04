package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.EnvironmentProvider;
import com.betx.application.port.out.TelegramBotGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.TelegramConfig;
import com.betx.domain.telegram.TelegramUpdate;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramConnectionServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void reportsDisabledStatus() {
        TelegramConnectionService service = service(configWithTelegram(new TelegramConfig(false, null, null, null, null, null, null, null, null, null)));

        assertThat(service.status(CONFIG_PATH)).isEqualTo("Telegram is disabled in betx.yml.");
    }

    @Test
    void reportsNotConnectedStatus() {
        TelegramConnectionService service = service(configWithTelegram(new TelegramConfig(true, null, null, null, null, null, null, null, null, null)));

        assertThat(service.status(CONFIG_PATH)).isEqualTo("Telegram is not connected.\nConnect it with:\n  betx telegram connect");
    }

    @Test
    void sendsMessageOnlyWhenTelegramIsConnected() {
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        TelegramConnectionService service = service(
            configWithTelegram(new TelegramConfig(true, "token", null, null, null, "12345", "2026-05-31T12:00:00Z", null, null, null)),
            Map.of(),
            gateway
        );

        boolean sent = service.sendMessageIfConnected(CONFIG_PATH, "hello");

        assertThat(sent).isTrue();
        assertThat(gateway.sentMessages()).containsExactly(new SentMessage("token", "12345", "hello"));
    }

    @Test
    void doesNotSendMessageWhenTokenOrChatIdIsMissing() {
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        TelegramConnectionService service = service(configWithTelegram(new TelegramConfig(true, null, null, null, null, null, null, null, null, null)), Map.of(), gateway);

        assertThat(service.sendMessageIfConnected(CONFIG_PATH, "hello")).isFalse();
        assertThat(gateway.sentMessages()).isEmpty();
    }

    @Test
    void sendTestMessageFailsWhenTelegramIsNotConnected() {
        TelegramConnectionService service = service(configWithTelegram(new TelegramConfig(true, "token", null, null, null, null, null, null, null, null)));

        assertThatThrownBy(() -> service.sendTestMessage(CONFIG_PATH))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Telegram is not connected.");
    }

    @Test
    void connectStoresPendingCodeAndConfirmsMatchingStartUpdate() {
        RecordingConfigRepository repository = new RecordingConfigRepository(
            configWithTelegram(new TelegramConfig(true, null, null, null, null, null, null, null, null, null))
        );
        RecordingTelegramGateway gateway = new RecordingTelegramGateway();
        gateway.botUsername = "betx_bot";
        gateway.updates = List.of(new TelegramUpdate(10L, "12345", "/start link-code", "user", "Jose"));
        TelegramConnectionService service = new TelegramConnectionService(
            repository,
            new MapEnvironmentProvider(Map.of()),
            gateway,
            CLOCK,
            () -> "link-code"
        );
        List<String> deepLinks = new ArrayList<>();

        var result = service.connect(CONFIG_PATH, 0, () -> "token", deepLinks::add);

        assertThat(result.connected()).isTrue();
        assertThat(result.chatId()).isEqualTo("12345");
        assertThat(deepLinks).containsExactly("https://t.me/betx_bot?start=link-code");
        assertThat(repository.savedFields()).hasSize(2);
        assertThat(repository.savedFields().get(0)).containsEntry("bot_token", "token").containsEntry("pending_link_code", "link-code");
        assertThat(repository.savedFields().get(1))
            .containsEntry("chat_id", "12345")
            .containsEntry("connected_at", "2026-05-31T12:00:00Z")
            .containsEntry("username", "user")
            .containsEntry("first_name", "Jose");
        assertThat(gateway.sentMessages()).containsExactly(new SentMessage("token", "12345", TelegramConnectionService.CONFIRMATION_MESSAGE));
    }

    private TelegramConnectionService service(BetxConfig config) {
        return service(config, Map.of(), new RecordingTelegramGateway());
    }

    private TelegramConnectionService service(BetxConfig config, Map<String, String> environment, RecordingTelegramGateway gateway) {
        return new TelegramConnectionService(new RecordingConfigRepository(config), new MapEnvironmentProvider(environment), gateway, CLOCK, () -> "link-code");
    }

    private BetxConfig configWithTelegram(TelegramConfig telegram) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            telegram,
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );
    }

    private record MapEnvironmentProvider(Map<String, String> values) implements EnvironmentProvider {
        @Override
        public String get(String name) {
            return values.get(name);
        }
    }

    private static final class RecordingConfigRepository implements BetxConfigRepository {
        private final BetxConfig config;
        private final List<Map<String, Object>> savedFields = new ArrayList<>();

        private RecordingConfigRepository(BetxConfig config) {
            this.config = config;
        }

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
            savedFields.add(new HashMap<>(fields));
        }

        private List<Map<String, Object>> savedFields() {
            return savedFields;
        }
    }

    private static final class RecordingTelegramGateway implements TelegramBotGateway {
        private final List<SentMessage> sentMessages = new ArrayList<>();
        private String botUsername = "bot";
        private List<TelegramUpdate> updates = List.of();

        @Override
        public String getBotUsername(String token) {
            return botUsername;
        }

        @Override
        public List<TelegramUpdate> getUpdates(String token, Long offset, int timeoutSeconds) {
            return updates;
        }

        @Override
        public void sendMessage(String token, String chatId, String text) {
            sentMessages.add(new SentMessage(token, chatId, text));
        }

        private List<SentMessage> sentMessages() {
            return sentMessages;
        }
    }

    private record SentMessage(String token, String chatId, String text) {
    }
}
