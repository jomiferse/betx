package com.betx.application.port.out;

import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;

/** Executes candidate orders. Real exchange execution is intentionally not implemented yet. */
public interface BetExecutionGateway {
    BetExecutionResult execute(BetOrder order);

    default BetExecutionResult execute(ConfigPath configPath, BetOrder order) {
        return execute(order);
    }
}
