package com.betx.adapter.betfair;

import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.ExchangeExposureGateway;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.exposure.ExchangeExposure;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component("betfairExchangeExposureGateway")
public class BetfairExchangeExposureGateway implements ExchangeExposureGateway {
    private final BetfairGateway gateway;

    public BetfairExchangeExposureGateway(BetfairGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ExchangeExposure exposure(BetxConfig config, String exchange, Instant settledSince) {
        if (!"betfair".equals(exchange)) {
            return ExchangeExposure.unavailable("Exchange exposure is not implemented for " + exchange + ".");
        }
        try {
            BetfairSession session = gateway.login(credentials(config, exchange));
            return gateway.readExposure(session, settledSince);
        } catch (RuntimeException exc) {
            String message = exc.getMessage();
            return ExchangeExposure.unavailable(message == null || message.isBlank()
                ? exc.getClass().getSimpleName()
                : message);
        }
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
