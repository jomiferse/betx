package com.betx.application.port.out;

import com.betx.domain.config.BetxConfig;
import java.math.BigDecimal;
import java.util.Optional;

/** Reads the available betting balance for a configured exchange. */
public interface ExchangeAccountGateway {
    Optional<BigDecimal> availableBalance(BetxConfig config, String exchange);
}
