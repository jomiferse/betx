package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceStatusService {
    private final Supplier<BetxInterfaceRuntimeService.RuntimeState> runtimeState;
    private final BetxConfigRepository configRepository;
    private final ExchangeAccountGateway accountGateway;
    private final BetxInterfaceProperties properties;

    @Autowired
    public BetxInterfaceStatusService(
        BetxInterfaceRuntimeService runtimeService,
        BetxConfigRepository configRepository,
        ExchangeAccountGateway accountGateway,
        BetxInterfaceProperties properties
    ) {
        this(runtimeService::state, configRepository, accountGateway, properties);
    }

    BetxInterfaceStatusService(
        Supplier<BetxInterfaceRuntimeService.RuntimeState> runtimeState,
        BetxConfigRepository configRepository,
        ExchangeAccountGateway accountGateway,
        BetxInterfaceProperties properties
    ) {
        this.runtimeState = runtimeState;
        this.configRepository = configRepository;
        this.accountGateway = accountGateway;
        this.properties = properties;
    }

    public BetxInterfaceStatusView status() {
        BetxInterfaceRuntimeService.RuntimeState state = runtimeState.get();
        BetxConfig config;
        try {
            config = configRepository.load(new ConfigPath(properties.configPath()));
        } catch (RuntimeException exc) {
            return new BetxInterfaceStatusView(
                InterfaceStatus.NEEDS_ATTENTION,
                "BetX necesita atencion antes de continuar.",
                null,
                state.updatedAt(),
                state.lastCycleAt(),
                false
            );
        }
        BigDecimal availableBalance = config.enabledExchanges().stream()
            .findFirst()
            .flatMap(exchange -> safeBalance(config, exchange.name()))
            .orElse(null);
        boolean manualConfirmationEnabled = config.enabledExchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .anyMatch(exchange -> exchange.betfair().autoBetting().enabled()
                && exchange.betfair().autoBetting().requestConfirmation());
        return new BetxInterfaceStatusView(
            state.status(),
            state.message(),
            availableBalance,
            state.updatedAt(),
            state.lastCycleAt(),
            manualConfirmationEnabled
        );
    }

    private Optional<BigDecimal> safeBalance(BetxConfig config, String exchange) {
        try {
            return accountGateway.availableBalance(config, exchange);
        } catch (RuntimeException exc) {
            return Optional.empty();
        }
    }
}
