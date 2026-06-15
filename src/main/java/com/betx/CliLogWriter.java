package com.betx;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Duplicates CLI console output to daily log files without replacing console behavior. */
class CliLogWriter {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final Path directory;
    private final Clock clock;

    CliLogWriter(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    PrintStream loggingPrintStream(PrintStream original) {
        return new PrintStream(new LoggingOutputStream(original), true, StandardCharsets.UTF_8);
    }

    private Path logFile() {
        return directory.resolve("messages_" + LocalDate.now(clock).format(FILE_DATE) + ".txt");
    }

    private void writeLine(String line) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(
            logFile(),
            clock.instant() + " | " + line + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private final class LoggingOutputStream extends OutputStream {
        private final PrintStream original;
        private final StringBuilder line = new StringBuilder();

        private LoggingOutputStream(PrintStream original) {
            this.original = original;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            original.write(value);
            if (value == '\n') {
                flushLine();
                return;
            }
            if (value != '\r') {
                line.append((char) value);
            }
        }

        @Override
        public synchronized void flush() {
            original.flush();
        }

        @Override
        public synchronized void close() {
            flush();
        }

        private void flushLine() throws IOException {
            writeLine(line.toString());
            line.setLength(0);
        }
    }
}
