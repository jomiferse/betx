package com.betx.adapter.betfair;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.PaperTradeSettlementGateway;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ExchangeConfig;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Read-only Betfair market-book settlement adapter for paper trades. */
@Component
public class BetfairPaperTradeSettlementGateway implements PaperTradeSettlementGateway {
    private static final String EXCHANGE_NAME = "betfair";

    private final BetfairGateway gateway;

    public BetfairPaperTradeSettlementGateway(BetfairGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String exchangeName() {
        return EXCHANGE_NAME;
    }

    @Override
    public Optional<BacktestOutcome> outcome(BetxConfig config, PaperTrade trade) {
        Optional<ExchangeConfig> exchange = config.enabledExchanges().stream()
            .filter(candidate -> EXCHANGE_NAME.equals(candidate.name()))
            .findFirst();
        if (exchange.isEmpty() || !exchange.get().betfair().isConfigured()) {
            return Optional.empty();
        }
        var betfair = exchange.get().betfair();
        var session = gateway.login(new BetfairCredentials(
            betfair.username(),
            betfair.password(),
            betfair.appKey(),
            betfair.country()
        ));
        return gateway.listMarketBook(session, List.of(trade.marketId())).stream()
            .filter(book -> "CLOSED".equalsIgnoreCase(book.status()))
            .findFirst()
            .flatMap(book -> outcome(book, trade.selectionId()));
    }

    private Optional<BacktestOutcome> outcome(BetfairMarketBook book, long selectionId) {
        return book.runners().stream()
            .filter(runner -> runner.selectionId() == selectionId)
            .map(BetfairRunnerPrice::status)
            .filter(status -> status != null && !status.isBlank())
            .map(status -> status.toUpperCase(Locale.ROOT))
            .flatMap(status -> switch (status) {
                case "WINNER" -> java.util.stream.Stream.of(BacktestOutcome.WIN);
                case "LOSER" -> java.util.stream.Stream.of(BacktestOutcome.LOSE);
                default -> java.util.stream.Stream.empty();
            })
            .findFirst();
    }
}
