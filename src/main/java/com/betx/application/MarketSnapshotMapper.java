package com.betx.application;

import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converts Betfair read models into normalized strategy snapshots. */
@Component
public class MarketSnapshotMapper {
    /** Combines market catalogue metadata with market book runner prices. */
    public List<MarketSnapshot> toSnapshots(List<BetfairMarketCatalogue> catalogues, List<BetfairMarketBook> books) {
        return toSnapshots("betfair", catalogues, books);
    }

    /** Combines market catalogue metadata with market book runner prices for one exchange. */
    public List<MarketSnapshot> toSnapshots(String exchange, List<BetfairMarketCatalogue> catalogues, List<BetfairMarketBook> books) {
        Map<String, BetfairMarketCatalogue> catalogueByMarketId = catalogues.stream()
            .collect(Collectors.toMap(BetfairMarketCatalogue::marketId, Function.identity(), (left, right) -> left));

        List<MarketSnapshot> snapshots = new ArrayList<>();
        for (BetfairMarketBook book : books) {
            BetfairMarketCatalogue catalogue = catalogueByMarketId.get(book.marketId());
            if (catalogue == null) {
                continue;
            }
            for (BetfairRunnerPrice runner : book.runners()) {
                snapshots.add(toSnapshot(exchange, catalogue, book, runner));
            }
        }
        return snapshots;
    }

    private MarketSnapshot toSnapshot(String exchange, BetfairMarketCatalogue catalogue, BetfairMarketBook book, BetfairRunnerPrice runner) {
        return new MarketSnapshot(
            exchange,
            catalogue.marketId(),
            catalogue.marketName(),
            catalogue.eventName(),
            catalogue.competitionName(),
            catalogue.marketStartTime(),
            runner.selectionId(),
            catalogue.runnerName(runner.selectionId()).orElse(null),
            runner.bestBackPrice(),
            runner.bestLayPrice(),
            relativeSpread(runner.bestBackPrice(), runner.bestLayPrice()),
            liquidity(book.totalMatched(), runner.totalMatched())
        );
    }

    private BigDecimal relativeSpread(BigDecimal bestBackPrice, BigDecimal bestLayPrice) {
        if (bestBackPrice == null || bestLayPrice == null || bestBackPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return bestLayPrice.subtract(bestBackPrice).divide(bestBackPrice, 8, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal liquidity(BigDecimal marketLiquidity, BigDecimal runnerLiquidity) {
        if (marketLiquidity != null) {
            return marketLiquidity;
        }
        return runnerLiquidity == null ? BigDecimal.ZERO : runnerLiquidity;
    }
}
