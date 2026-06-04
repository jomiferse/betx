package com.betx.cli;

import com.betx.application.TelegramConnectionService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "status", description = "Show Telegram connection status.")
public class TelegramStatusCommand implements Runnable {
    private final TelegramConnectionService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    public TelegramStatusCommand(TelegramConnectionService service) {
        this.service = service;
    }

    @Override
    public void run() {
        System.out.println(service.status(new ConfigPath(configPath)));
    }
}
