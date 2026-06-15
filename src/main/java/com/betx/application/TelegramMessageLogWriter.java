package com.betx.application;

import com.betx.domain.config.BetxConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Writes successfully sent Telegram message text to daily audit files. */
public class TelegramMessageLogWriter {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final Path directory;
    private final Clock clock;
    private final boolean enabled;

    public TelegramMessageLogWriter() {
        this(Path.of("logs", "telegram"), Clock.systemDefaultZone(), true);
    }

    public TelegramMessageLogWriter(Path directory, Clock clock) {
        this(directory, clock, true);
    }

    private TelegramMessageLogWriter(Path directory, Clock clock, boolean enabled) {
        this.directory = directory;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.enabled = enabled;
    }

    public static TelegramMessageLogWriter disabled() {
        return new TelegramMessageLogWriter(Path.of("logs", "telegram"), Clock.systemDefaultZone(), false);
    }

    public void recordSentMessage(BetxConfig config, String text) {
        if (!enabled || config == null || !isInfo(config.app().logLevel())) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(
                logFile(),
                line(text),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exc) {
            throw new IllegalStateException("Could not write Telegram message log.", exc);
        }
    }

    private Path logFile() {
        String date = LocalDate.now(clock).format(FILE_DATE);
        return directory.resolve("messages_" + date + ".txt");
    }

    private String line(String text) {
        String safeText = text == null ? "" : text.replace("\r", "\\r").replace("\n", "\\n");
        return clock.instant() + " | " + safeText + System.lineSeparator();
    }

    private boolean isInfo(String logLevel) {
        return "info".equalsIgnoreCase(logLevel == null ? "" : logLevel.strip());
    }
}
