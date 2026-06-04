package com.betx.adapter.betfair;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import org.springframework.stereotype.Component;

@Component("betfairBetExecutionGateway")
public class BetfairBetExecutionGateway implements BetExecutionGateway {
    private final BetxConfigRepository configRepository;
    private final BetfairGateway gateway;

    public BetfairBetExecutionGateway(BetxConfigRepository configRepository, BetfairGateway gateway) {
        this.configRepository = configRepository;
        this.gateway = gateway;
    }

    @Override
    public BetExecutionResult execute(BetOrder order) {
        return BetExecutionResult.rejected("Live bet execution requires a configuration path.");
    }

    @Override
    public BetExecutionResult execute(ConfigPath configPath, BetOrder order) {
        BetxConfig config = configRepository.load(configPath);
        BetfairCredentials credentials = credentials(config, order.exchange());
        BetfairSession session = gateway.login(credentials);
        return gateway.placeOrder(session, order);
    }

    private BetfairCredentials credentials(BetxConfig config, String exchangeName) {
        BetfairConfig betfairConfig = config.exchanges().stream()
            .filter(exchange -> exchangeName.equals(exchange.name()))
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
