package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.BetxConfigValidator;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.startup.StartupStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StartBetxService {
    private final BetxConfigRepository configRepository;
    private final BetxConfigValidator validator;

    @Autowired
    public StartBetxService(BetxConfigRepository configRepository) {
        this(configRepository, new BetxConfigValidator());
    }

    StartBetxService(BetxConfigRepository configRepository, BetxConfigValidator validator) {
        this.configRepository = configRepository;
        this.validator = validator;
    }

    public StartupStatus start(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        validator.validate(config);
        boolean autoBettingEnabled = config.enabledExchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .anyMatch(exchange -> exchange.betfair().autoBetting().enabled());
        boolean requestConfirmation = config.enabledExchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .anyMatch(exchange -> exchange.betfair().autoBetting().enabled()
                && exchange.betfair().autoBetting().requestConfirmation());
        return new StartupStatus(
            config.telegram().enabled(),
            config.ml().enabled(),
            autoBettingEnabled,
            requestConfirmation,
            config.storage().path(),
            config.marketData().pollIntervalSeconds()
        );
    }
}
