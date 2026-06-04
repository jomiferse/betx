package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.InitializeProjectService;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsCreatedProjectFiles() {
        InitCommand command = new InitCommand(new InitializeProjectService(new RecordingConfigRepository(true)), tempDir);

        String output = captureOutput(command::run);

        assertThat(output).contains("BetX project files are ready.");
        assertThat(output).contains("  - betx.yml");
        assertThat(output).contains("  - data/");
        assertThat(output).contains("  - models/");
    }

    @Test
    void omitsConfigLineWhenConfigWasNotWritten() {
        InitCommand command = new InitCommand(new InitializeProjectService(new RecordingConfigRepository(false)), tempDir);

        String output = captureOutput(command::run);

        assertThat(output).doesNotContain("  - betx.yml");
        assertThat(output).contains("  - data/");
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

    private record RecordingConfigRepository(boolean written) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return BetxConfig.defaults();
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return written;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }
}
