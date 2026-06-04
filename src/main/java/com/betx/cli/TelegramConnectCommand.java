package com.betx.cli;

import com.betx.application.TelegramConnectionService;
import com.betx.domain.config.ConfigPath;
import java.io.Console;
import java.nio.file.Path;
import java.util.Scanner;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "connect", description = "Connect Telegram alerts by opening a one-time deep link.")
public class TelegramConnectCommand implements Runnable {
    private final TelegramConnectionService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--timeout", defaultValue = "120", description = "Seconds to wait for Telegram /start.")
    long timeout;

    public TelegramConnectCommand(TelegramConnectionService service) {
        this.service = service;
    }

    @Override
    public void run() {
        ConfigPath path = new ConfigPath(configPath);
        if (!service.isEnabled(path)) {
            System.out.println("Telegram is disabled in betx.yml.");
            return;
        }
        if (service.isConnected(path) && !confirmReconnect()) {
            System.out.println("Telegram is already connected.");
            return;
        }

        var result = service.connect(path, timeout, this::promptToken, this::printConnectionInstructions);
        if (result.connected()) {
            System.out.println("Telegram connected successfully ✅");
        } else {
            System.out.println("Telegram connection was not completed. You can retry with:");
            System.out.println("  betx telegram connect");
        }
    }

    private void printConnectionInstructions(String deepLink) {
        System.out.println(deepLink);
        System.out.println("Open this link, press Start in Telegram, then return to the terminal.");
        System.out.println("Waiting for Telegram connection...");
    }

    private boolean confirmReconnect() {
        Console console = System.console();
        String answer = console == null
            ? "n"
            : console.readLine("Telegram is already connected. Do you want to reconnect? [y/N] ");
        return answer != null && answer.strip().equalsIgnoreCase("y");
    }

    private String promptToken() {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword(
                "Telegram bot token is missing. Create a bot with BotFather and paste the token here: "
            );
            return password == null ? null : new String(password);
        }
        System.out.print("Telegram bot token: ");
        return new Scanner(System.in).nextLine();
    }
}
