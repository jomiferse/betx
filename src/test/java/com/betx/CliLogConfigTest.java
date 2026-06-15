package com.betx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliLogConfigTest {
    @Test
    void enablesCliLoggingWhenConfiguredLogLevelIsInfo(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("betx.yml");
        Files.writeString(config, """
            app:
              log_level: info
            """);

        CliLogConfig cliLogConfig = CliLogConfig.fromArgs(new String[] {"start", "--config", config.toString()});

        assertThat(cliLogConfig.enabled()).isTrue();
        assertThat(cliLogConfig.directory()).isEqualTo(Path.of("logs", "cli"));
    }

    @Test
    void disablesCliLoggingWhenConfiguredLogLevelIsNotInfo(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("betx.yml");
        Files.writeString(config, """
            app:
              log_level: warn
            """);

        assertThat(CliLogConfig.fromArgs(new String[] {"start", "--config", config.toString()}).enabled()).isFalse();
    }

    @Test
    void routesPaperTradeCliLoggingToSeparateDirectory(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("betx.yml");
        Files.writeString(config, """
            app:
              log_level: info
            """);

        CliLogConfig cliLogConfig = CliLogConfig.fromArgs(new String[] {"paper-trade", "--config", config.toString()});

        assertThat(cliLogConfig.enabled()).isTrue();
        assertThat(cliLogConfig.directory()).isEqualTo(Path.of("logs", "paper-trade"));
    }
}
