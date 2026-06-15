package com.betx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Minimal startup configuration for CLI audit logging before Spring is available. */
record CliLogConfig(boolean enabled, Path directory) {
    static CliLogConfig fromArgs(String[] args) {
        Path configPath = configPath(args);
        if (!Files.exists(configPath)) {
            return new CliLogConfig(false, directory(args));
        }
        return new CliLogConfig("info".equals(readLogLevel(configPath)), directory(args));
    }

    private static Path configPath(String[] args) {
        if (args == null) {
            return Path.of("betx.yml");
        }
        for (int index = 0; index < args.length - 1; index++) {
            if ("--config".equals(args[index]) || "-c".equals(args[index])) {
                return Path.of(args[index + 1]);
            }
        }
        return Path.of("betx.yml");
    }

    private static Path directory(String[] args) {
        return "paper-trade".equals(commandName(args))
            ? Path.of("logs", "paper-trade")
            : Path.of("logs", "cli");
    }

    private static String commandName(String[] args) {
        if (args == null) {
            return "";
        }
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if ("--config".equals(arg) || "-c".equals(arg)) {
                index++;
                continue;
            }
            if (arg != null && !arg.startsWith("-")) {
                return arg;
            }
        }
        return "";
    }

    private static String readLogLevel(Path configPath) {
        try {
            boolean inApp = false;
            for (String rawLine : Files.readAllLines(configPath)) {
                String line = rawLine.strip();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!rawLine.startsWith(" ") && line.endsWith(":")) {
                    inApp = "app:".equals(line);
                    continue;
                }
                if (inApp && line.startsWith("log_level:")) {
                    return cleanValue(line.substring("log_level:".length()));
                }
            }
            return "";
        } catch (IOException exc) {
            return "";
        }
    }

    private static String cleanValue(String value) {
        String cleaned = value == null ? "" : value.strip();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
            || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
