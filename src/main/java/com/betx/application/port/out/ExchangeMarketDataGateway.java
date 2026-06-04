package com.betx.application.port.out;

import com.betx.application.ExchangeMarketDataResult;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.MarketSnapshot;
import java.util.List;

/** Reads normalized market data from a configured exchange. */
public interface ExchangeMarketDataGateway {
    String exchangeName();

    List<MarketSnapshot> listSnapshots(ExchangeConfig exchange);

    default ExchangeMarketDataResult listMarketData(ExchangeConfig exchange) {
        return new ExchangeMarketDataResult(listSnapshots(exchange), 0, 0);
    }
}
