package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.BetIntentService;
import com.betx.application.TelegramConnectionService;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StorageConfig;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramCommandTest {
    @Test
    void statusCommandPrintsServiceStatus() {
        RecordingTelegramService service = new RecordingTelegramService();
        service.status = "Telegram is connected.";
        TelegramStatusCommand command = new TelegramStatusCommand(service);
        command.configPath = Path.of("custom.yml");

        String output = captureOutput(command::run);

        assertThat(output).isEqualTo("Telegram is connected.\n");
        assertThat(service.configPath).isEqualTo(new ConfigPath(Path.of("custom.yml")));
    }

    @Test
    void testCommandSendsTestMessage() {
        RecordingTelegramService service = new RecordingTelegramService();
        TelegramTestCommand command = new TelegramTestCommand(service);
        command.configPath = Path.of("custom.yml");

        String output = captureOutput(command::run);

        assertThat(output).isEqualTo("Telegram test message sent.\n");
        assertThat(service.testMessageConfigPath).isEqualTo(new ConfigPath(Path.of("custom.yml")));
    }

    @Test
    void betsCommandListsRecentIntents() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", intent("intent-1", BetIntentStage.AWAITING_CONFIRMATION));
        TelegramBetsCommand command = new TelegramBetsCommand(new BetIntentService(new StaticConfigRepository(), intents));
        command.configPath = Path.of("custom.yml");
        command.limit = 20;

        String output = captureOutput(command::run);

        assertThat(output).contains("id=intent-1");
        assertThat(output).contains("source=TELEGRAM_CONFIRMATION");
        assertThat(output).contains("stage=AWAITING_CONFIRMATION");
        assertThat(output).contains("event=Team A v Team B");
        assertThat(output).contains("runner=Team A");
        assertThat(output).contains("stake=n/a");
    }

    @Test
    void betsCancelCommandCancelsPendingIntent() {
        RecordingIntentRepository intents = new RecordingIntentRepository();
        intents.save("data.db", intent("intent-1", BetIntentStage.AWAITING_STAKE));
        TelegramBetsCancelCommand command = new TelegramBetsCancelCommand(new BetIntentService(new StaticConfigRepository(), intents));
        command.configPath = Path.of("custom.yml");
        command.id = "intent-1";

        String output = captureOutput(command::run);

        assertThat(output).contains("BET INTENT CANCELLED | id=intent-1");
        assertThat(output).contains("Bet intent cancelled: intent-1");
        assertThat(intents.findById("data.db", "intent-1"))
            .hasValueSatisfying(intent -> assertThat(intent.stage()).isEqualTo(BetIntentStage.CANCELLED));
    }

    @Test
    void connectCommandFailsFastWhenNoConsoleIsAvailableForTokenPrompt() {
        RecordingTelegramService service = new RecordingTelegramService();
        service.enabled = true;
        service.connected = false;
        service.invokeTokenPrompt = true;
        TelegramConnectCommand command = new TelegramConnectCommand(service);
        command.configPath = Path.of("custom.yml");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            assertThatThrownBy(command::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Interactive Telegram token entry requires a real terminal. Set TELEGRAM_BOT_TOKEN or run `betx telegram connect` from a terminal with console access.");
        } finally {
            System.setOut(original);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).doesNotContain("secret-token");
        assertThat(rendered).doesNotContain("Telegram bot token: ");
    }

    private String captureOutput(Runnable runnable) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private BetIntent intent(String id, BetIntentStage stage) {
        return new BetIntent(
            id,
            "betfair",
            "1.1",
            42L,
            "Team A v Team B",
            "Match Odds",
            "Team A",
            "liquidity_ok",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(5),
            null,
            null,
            null,
            stage,
            Instant.parse("2026-06-05T08:00:00Z"),
            Instant.parse("2026-06-05T09:00:00Z")
        );
    }

    private static final class StaticConfigRepository implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            BetxConfig defaults = BetxConfig.defaults();
            return new BetxConfig(
                defaults.app(),
                defaults.telegram(),
                defaults.betfair(),
                defaults.exchanges(),
                defaults.marketData(),
                new StorageConfig(defaults.storage().type(), "data.db"),
                defaults.risk(),
                defaults.strategies(),
                defaults.ml()
            );
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private static final class RecordingIntentRepository implements BetIntentRepository {
        private final List<BetIntent> intents = new ArrayList<>();

        @Override
        public Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public Optional<BetIntent> findLatestByKeySince(
            String databasePath,
            String exchange,
            String marketId,
            long selectionId,
            Instant since
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<BetIntent> findById(String databasePath, String id) {
            return intents.stream().filter(intent -> intent.id().equals(id)).findFirst();
        }

        @Override
        public List<BetIntent> listRecent(String databasePath, int limit) {
            return intents.stream().limit(limit).toList();
        }

        @Override
        public List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit) {
            return intents.stream()
                .filter(intent -> stages.contains(intent.stage()))
                .limit(limit)
                .toList();
        }

        @Override
        public long countByStages(String databasePath, List<BetIntentStage> stages) {
            return 0L;
        }

        @Override
        public BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since) {
            return BigDecimal.ZERO;
        }

        @Override
        public void save(String databasePath, BetIntent intent) {
            intents.add(intent);
        }

        @Override
        public void update(String databasePath, BetIntent intent) {
            for (int index = 0; index < intents.size(); index++) {
                if (intents.get(index).id().equals(intent.id())) {
                    intents.set(index, intent);
                    return;
                }
            }
            intents.add(intent);
        }

    }

    private static final class RecordingTelegramService extends TelegramConnectionService {
        private String status;
        private ConfigPath configPath;
        private ConfigPath testMessageConfigPath;
        private boolean enabled;
        private boolean connected;
        private boolean invokeTokenPrompt;

        private RecordingTelegramService() {
            super(null, null, null);
        }

        @Override
        public String status(ConfigPath configPath) {
            this.configPath = configPath;
            return status;
        }

        @Override
        public void sendTestMessage(ConfigPath configPath) {
            this.testMessageConfigPath = configPath;
        }

        @Override
        public boolean isEnabled(ConfigPath configPath) {
            return enabled;
        }

        @Override
        public boolean isConnected(ConfigPath configPath) {
            return connected;
        }

        @Override
        public com.betx.domain.telegram.TelegramConnectionResult connect(
            ConfigPath configPath,
            long timeoutSeconds,
            java.util.function.Supplier<String> tokenPrompt,
            java.util.function.Consumer<String> deepLinkConsumer
        ) {
            if (invokeTokenPrompt) {
                tokenPrompt.get();
            }
            return new com.betx.domain.telegram.TelegramConnectionResult(false, "unused", null);
        }
    }
}
