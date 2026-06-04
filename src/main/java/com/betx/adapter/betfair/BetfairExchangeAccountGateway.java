package com.betx.adapter.betfair;

import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ExchangeConfig;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BetfairExchangeAccountGateway implements ExchangeAccountGateway {
    private final BetfairGateway gateway;

    public BetfairExchangeAccountGateway(BetfairGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Optional<BigDecimal> availableBalance(BetxConfig config, String exchange) {
        BetfairCredentials credentials = credentials(config, exchange);
        BetfairSession session = gateway.login(credentials);
        return Optional.ofNullable(gateway.getAccountFunds(session));
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
