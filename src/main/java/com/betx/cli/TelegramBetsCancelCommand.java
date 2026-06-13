package com.betx.cli;

import com.betx.application.BetIntentService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "cancel", description = "Cancel a pending bet intent.")
public class TelegramBetsCancelCommand implements Runnable {
    private final BetIntentService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--id", required = true, description = "Bet intent id.")
    String id;

    public TelegramBetsCancelCommand(BetIntentService service) {
        this.service = service;
    }

    @Override
    public void run() {
        var intent = service.cancel(new ConfigPath(configPath), id, System.out::println);
        System.out.println("Bet intent cancelled: " + intent.id());
    }
}
