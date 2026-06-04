package com.betx.application;

import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BetfairIntegrationService {
    private final BetxConfigRepository configRepository;
    private final BetfairGateway gateway;

    public BetfairIntegrationService(BetxConfigRepository configRepository, BetfairGateway gateway) {
        this.configRepository = configRepository;
        this.gateway = gateway;
    }

    public BetfairSession authenticate(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        return gateway.login(credentials(config));
    }

    public List<BetfairMarketCatalogue> listMarkets(ConfigPath configPath, String eventTypeId, int maxResults) {
        BetxConfig config = configRepository.load(configPath);
        BetfairSession session = gateway.login(credentials(config));
        return gateway.listMarketCatalogue(session, new BetfairMarketQuery(List.of(eventTypeId), List.of(), maxResults));
    }

    public List<BetfairMarketBook> listMarketBooks(ConfigPath configPath, List<String> marketIds) {
        BetxConfig config = configRepository.load(configPath);
        BetfairSession session = gateway.login(credentials(config));
        return gateway.listMarketBook(session, marketIds);
    }

    private BetfairCredentials credentials(BetxConfig config) {
        var betfairConfig = config.exchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .findFirst()
            .map(ExchangeConfig::betfair)
            .orElse(config.betfair());
        if (betfairConfig == null || !betfairConfig.isConfigured()) {
            throw new IllegalStateException("Betfair credentials are missing from betx.yml.");
        }
        return new BetfairCredentials(
            betfairConfig.username(),
            betfairConfig.password(),
            betfairConfig.appKey(),
            betfairConfig.country()
        );
    }
}
