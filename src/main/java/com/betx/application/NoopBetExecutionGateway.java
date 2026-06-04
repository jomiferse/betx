package com.betx.application;

import com.betx.application.port.out.BetExecutionGateway;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Blocks every order until live execution adapters are explicitly implemented. */
@Component
@Primary
public class NoopBetExecutionGateway implements BetExecutionGateway {
    public static final String LIVE_NOT_IMPLEMENTED = "Live order execution is not implemented for configured exchanges.";

    @Override
    public BetExecutionResult execute(BetOrder order) {
        return BetExecutionResult.rejected(LIVE_NOT_IMPLEMENTED);
    }
}
