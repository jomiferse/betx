package com.betx.cli;

import com.betx.application.BetfairIntegrationService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "test", description = "Validate Betfair credentials and login.")
public class BetfairTestCommand implements Runnable {
    private final BetfairIntegrationService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    public BetfairTestCommand(BetfairIntegrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.authenticate(new ConfigPath(configPath));
        System.out.println("Betfair authentication successful.");
    }
}
