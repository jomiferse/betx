package com.betx.application;

import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RunnerType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        String runnerName = catalogue.runnerName(runner.selectionId()).orElse(null);
        return new MarketSnapshot(
            exchange,
            catalogue.marketId(),
            catalogue.marketName(),
            catalogue.eventName(),
            catalogue.competitionName(),
            catalogue.marketStartTime(),
            runner.selectionId(),
            runnerName,
            runnerType(catalogue, runnerName),
            runner.bestBackPrice(),
            runner.bestLayPrice(),
            relativeSpread(runner.bestBackPrice(), runner.bestLayPrice()),
            liquidity(book.totalMatched(), runner.totalMatched())
        );
    }

    private RunnerType runnerType(BetfairMarketCatalogue catalogue, String runnerName) {
        if (catalogue.marketName() == null || !"match odds".equalsIgnoreCase(catalogue.marketName().strip())) {
            return RunnerType.UNKNOWN;
        }
        String normalizedRunnerName = normalizeRunnerName(runnerName);
        if (normalizedRunnerName == null) {
            return RunnerType.UNKNOWN;
        }
        if ("draw".equals(normalizedRunnerName) || "the draw".equals(normalizedRunnerName)) {
            return RunnerType.DRAW;
        }
        String eventName = catalogue.eventName();
        if (eventName == null) {
            return RunnerType.UNKNOWN;
        }
        String[] teams = eventName.split("\\s+v\\s+", 2);
        if (teams.length != 2) {
            return RunnerType.UNKNOWN;
        }
        if (normalizedRunnerName.equals(normalizeRunnerName(teams[0]))) {
            return RunnerType.HOME;
        }
        if (normalizedRunnerName.equals(normalizeRunnerName(teams[1]))) {
            return RunnerType.AWAY;
        }
        return RunnerType.UNKNOWN;
    }

    private String normalizeRunnerName(String runnerName) {
        if (runnerName == null || runnerName.isBlank()) {
            return null;
        }
        return runnerName.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
