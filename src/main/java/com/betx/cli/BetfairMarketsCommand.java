package com.betx.cli;

import com.betx.application.BetfairIntegrationService;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "markets", description = "List active Betfair markets.")
public class BetfairMarketsCommand implements Runnable {
    private final BetfairIntegrationService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--event-type-id", defaultValue = "1", description = "Betfair event type id.")
    String eventTypeId;

    @Option(names = "--max-results", defaultValue = "5", description = "Maximum markets to return.")
    int maxResults;

    public BetfairMarketsCommand(BetfairIntegrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        List<BetfairMarketCatalogue> markets = service.listMarkets(new ConfigPath(configPath), eventTypeId, maxResults);
        if (markets.isEmpty()) {
            System.out.println("No active Betfair markets found.");
            return;
        }
        for (BetfairMarketCatalogue market : markets) {
            System.out.println(market.marketId() + " | " + market.marketName() + " | " + nullSafe(market.eventName()));
        }
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
