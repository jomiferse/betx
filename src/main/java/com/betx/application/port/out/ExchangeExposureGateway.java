package com.betx.application.port.out;

import com.betx.domain.config.BetxConfig;
import com.betx.domain.exposure.ExchangeExposure;
import java.time.Instant;

/** Reads real exchange exposure before any live order is executed. */
public interface ExchangeExposureGateway {
    ExchangeExposure exposure(BetxConfig config, String exchange, Instant settledSince);
}
