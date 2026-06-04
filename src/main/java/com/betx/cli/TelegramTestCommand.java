package com.betx.cli;

import com.betx.application.TelegramConnectionService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "test", description = "Send a Telegram Bot API test message.")
public class TelegramTestCommand implements Runnable {
    private final TelegramConnectionService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    public TelegramTestCommand(TelegramConnectionService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.sendTestMessage(new ConfigPath(configPath));
        System.out.println("Telegram test message sent.");
    }
}
