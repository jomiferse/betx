package com.betx.adapter.betfair;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.application.port.out.BetfairGateway;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetfairPaperTradeSettlementGatewayTest {
    @Test
    void mapsClosedWinnerRunnerToPaperWin() {
        BetfairPaperTradeSettlementGateway gateway = new BetfairPaperTradeSettlementGateway(new StaticBetfairGateway(
            new BetfairMarketBook(
                "1.234",
                "CLOSED",
                false,
                BigDecimal.ZERO,
                List.of(new BetfairRunnerPrice(2L, "WINNER", null, null, null, BigDecimal.ZERO))
            )
        ));

        assertThat(gateway.outcome(config(), trade())).contains(BacktestOutcome.WIN);
    }

    @Test
    void ignoresOpenMarketsWithoutSettledRunnerStatus() {
        BetfairPaperTradeSettlementGateway gateway = new BetfairPaperTradeSettlementGateway(new StaticBetfairGateway(
            new BetfairMarketBook(
                "1.234",
                "OPEN",
                false,
                BigDecimal.ZERO,
                List.of(new BetfairRunnerPrice(2L, "WINNER", null, null, null, BigDecimal.ZERO))
            )
        ));

        assertThat(gateway.outcome(config(), trade())).isEmpty();
    }

    private static BetxConfig config() {
        return BetxConfig.defaults().withExchanges(List.of(new ExchangeConfig(
            "betfair",
            true,
            new com.betx.domain.betfair.BetfairConfig("user", "password", "app-key")
        )));
    }

    private static PaperTrade trade() {
        return PaperTrade.recommended(new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            2L,
            "Draw",
            new BigDecimal("3.70"),
            new BigDecimal("3.80"),
            new BigDecimal("0.03"),
            new BigDecimal("1200")
        ), Instant.parse("2026-06-15T10:00:00Z"), BigDecimal.valueOf(5));
    }

    private record StaticBetfairGateway(BetfairMarketBook book) implements BetfairGateway {
        @Override
        public BetfairSession login(BetfairCredentials credentials) {
            return new BetfairSession("token", credentials.appKey());
        }

        @Override
        public List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query) {
            return List.of();
        }

        @Override
        public List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds) {
            return List.of(book);
        }
    }
}
