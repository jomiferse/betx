package com.betx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliLogWriterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T21:42:00Z"), ZoneOffset.UTC);

    @Test
    void duplicatesCliOutputToDailyFileWhenLogLevelIsInfo(@TempDir Path tempDir) throws Exception {
        CliLogWriter writer = new CliLogWriter(tempDir.resolve("logs").resolve("cli"), CLOCK);
        java.io.ByteArrayOutputStream console = new java.io.ByteArrayOutputStream();
        PrintStream stream = writer.loggingPrintStream(new PrintStream(console, true, StandardCharsets.UTF_8));

        stream.println("hello");
        stream.println("world");

        Path logFile = tempDir.resolve("logs").resolve("cli").resolve("messages_15062026.txt");
        assertThat(console.toString(StandardCharsets.UTF_8)).isEqualTo("hello\nworld\n");
        assertThat(Files.readString(logFile))
            .contains("2026-06-15T21:42:00Z | hello")
            .contains("2026-06-15T21:42:00Z | world");
    }
}
