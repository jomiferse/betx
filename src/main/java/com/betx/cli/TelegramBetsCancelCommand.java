package com.betx.cli;

import com.betx.application.TelegramBetIntentService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "cancel", description = "Cancel a pending Telegram bet confirmation.")
public class TelegramBetsCancelCommand implements Runnable {
    private final TelegramBetIntentService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--id", required = true, description = "Telegram bet intent id.")
    String id;

    public TelegramBetsCancelCommand(TelegramBetIntentService service) {
        this.service = service;
    }

    @Override
    public void run() {
        var intent = service.cancel(new ConfigPath(configPath), id);
        System.out.println("Telegram bet intent cancelled: " + intent.id());
    }
}
