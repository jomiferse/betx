package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.TelegramConnectionService;
import com.betx.domain.config.ConfigPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    private static final class RecordingTelegramService extends TelegramConnectionService {
        private String status;
        private ConfigPath configPath;
        private ConfigPath testMessageConfigPath;

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
    }
}
