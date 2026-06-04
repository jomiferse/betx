package com.betx.adapter.betfair;

import com.betx.application.ExchangeMarketDataResult;
import com.betx.application.MarketSnapshotMapper;
import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairEvent;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.MarketSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Exchange adapter that normalizes Betfair catalogue and book data. */
@Component
public class BetfairExchangeMarketDataGateway implements ExchangeMarketDataGateway {
    private static final String EXCHANGE_NAME = "betfair";
    private static final int BETFAIR_MAX_CATALOGUE_RESULTS = 1000;
    private static final int BETFAIR_WINDOW_CATALOGUE_RESULTS = 100;
    private static final int BETFAIR_MARKET_BOOK_BATCH_SIZE = 40;
    private static final Duration FULL_SCAN_LOOKAHEAD = Duration.ofDays(7);
    private static final Duration FULL_SCAN_WINDOW = Duration.ofHours(6);
    private static final Duration MIN_FULL_SCAN_WINDOW = Duration.ofMinutes(15);

    private final BetfairGateway gateway;
    private final MarketSnapshotMapper snapshotMapper;

    public BetfairExchangeMarketDataGateway(BetfairGateway gateway, MarketSnapshotMapper snapshotMapper) {
        this.gateway = gateway;
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public String exchangeName() {
        return EXCHANGE_NAME;
    }

    @Override
    public List<MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
        return listMarketData(exchange).snapshots();
    }

    @Override
    public ExchangeMarketDataResult listMarketData(ExchangeConfig exchange) {
        if (!EXCHANGE_NAME.equals(exchange.name())) {
            return new ExchangeMarketDataResult(List.of(), 0, 0);
        }
        if (!exchange.betfair().isConfigured()) {
            throw new IllegalStateException("Betfair credentials are missing from betx.yml.");
        }

        var session = gateway.login(new BetfairCredentials(
            exchange.betfair().username(),
            exchange.betfair().password(),
            exchange.betfair().appKey(),
            exchange.betfair().country()
        ));
        var marketData = exchange.marketData();
        if (marketData.scanAllMarkets() || marketData.maxMarkets() == 0) {
            return listAllConfiguredMarkets(session, exchange);
        }

        var catalogues = gateway.listMarketCatalogue(
            session,
            new BetfairMarketQuery(marketData.eventTypeIds(), marketData.marketTypeCodes(), marketData.maxMarkets())
        );
        return snapshotsFromCatalogues(session, catalogues, 0, 0);
    }

    private ExchangeMarketDataResult listAllConfiguredMarkets(BetfairSession session, ExchangeConfig exchange) {
        List<BetfairMarketCatalogue> broadCatalogues = listCataloguesWithoutMarketTypeFilter(session, exchange);
        if (!broadCatalogues.isEmpty()) {
            return snapshotsFromCatalogues(
                session,
                broadCatalogues,
                countEvents(broadCatalogues),
                countTestEvents(broadCatalogues)
            );
        }

        var marketData = exchange.marketData();
        List<BetfairEvent> events;
        try {
            events = gateway.listEvents(session, marketData.eventTypeIds(), marketData.marketTypeCodes());
        } catch (RuntimeException exc) {
            if (isTooMuchData(exc)) {
                var catalogues = listCataloguesWithoutMarketTypeFilter(session, exchange);
                return snapshotsFromCatalogues(session, catalogues, 0, 0);
            }
            throw exc;
        }
        List<BetfairEvent> eligibleEvents = events.stream()
            .filter(event -> !isTestEvent(event))
            .toList();
        int ignoredEvents = events.size() - eligibleEvents.size();
        List<BetfairMarketCatalogue> catalogues = new ArrayList<>();
        for (int start = 0; start < eligibleEvents.size(); start += marketData.betfairEventBatchSize()) {
            List<String> eventIds = eligibleEvents.stream()
                .skip(start)
                .limit(marketData.betfairEventBatchSize())
                .map(BetfairEvent::id)
                .toList();
            if (!eventIds.isEmpty()) {
                catalogues.addAll(gateway.listMarketCatalogue(
                    session,
                    new BetfairMarketQuery(
                        marketData.eventTypeIds(),
                        eventIds,
                        marketData.marketTypeCodes(),
                        BETFAIR_MAX_CATALOGUE_RESULTS
                    )
                ));
            }
        }
        return snapshotsFromCatalogues(session, catalogues, events.size(), ignoredEvents);
    }

    private ExchangeMarketDataResult snapshotsFromCatalogues(
        BetfairSession session,
        List<BetfairMarketCatalogue> catalogues,
        int eventsRead,
        int ignoredEvents
    ) {
        if (catalogues.isEmpty()) {
            return new ExchangeMarketDataResult(List.of(), eventsRead, ignoredEvents);
        }
        var marketIds = catalogues.stream().map(BetfairMarketCatalogue::marketId).toList();
        var books = listMarketBooksInBatches(session, marketIds);
        return new ExchangeMarketDataResult(snapshotMapper.toSnapshots(EXCHANGE_NAME, catalogues, books), eventsRead, ignoredEvents);
    }

    private List<com.betx.domain.betfair.BetfairMarketBook> listMarketBooksInBatches(BetfairSession session, List<String> marketIds) {
        List<com.betx.domain.betfair.BetfairMarketBook> books = new ArrayList<>();
        for (int start = 0; start < marketIds.size(); start += BETFAIR_MARKET_BOOK_BATCH_SIZE) {
            books.addAll(gateway.listMarketBook(
                session,
                marketIds.stream()
                    .skip(start)
                    .limit(BETFAIR_MARKET_BOOK_BATCH_SIZE)
                    .toList()
            ));
        }
        return books;
    }

    private boolean isTestEvent(BetfairEvent event) {
        return event.name() != null && event.name().toLowerCase(Locale.ROOT).contains("test");
    }

    private List<BetfairMarketCatalogue> listCataloguesWithoutMarketTypeFilter(BetfairSession session, ExchangeConfig exchange) {
        try {
            return gateway.listMarketCatalogue(
                    session,
                    new BetfairMarketQuery(
                        exchange.marketData().eventTypeIds(),
                        List.of(),
                        List.of(),
                        BETFAIR_MAX_CATALOGUE_RESULTS
                    )
                ).stream()
                .filter(catalogue -> matchesConfiguredMarketTypes(catalogue, exchange))
                .toList();
        } catch (RuntimeException exc) {
            if (isTooMuchData(exc)) {
                return listCataloguesByTimeWindow(session, exchange);
            }
            throw exc;
        }
    }

    private List<BetfairMarketCatalogue> listCataloguesByTimeWindow(BetfairSession session, ExchangeConfig exchange) {
        Map<String, BetfairMarketCatalogue> cataloguesByMarketId = new LinkedHashMap<>();
        Instant from = Instant.now();
        Instant deadline = from.plus(FULL_SCAN_LOOKAHEAD);
        while (from.isBefore(deadline)) {
            Instant to = from.plus(FULL_SCAN_WINDOW);
            if (to.isAfter(deadline)) {
                to = deadline;
            }
            for (BetfairMarketCatalogue catalogue : listCataloguesInWindow(session, exchange, from, to)) {
                if (matchesConfiguredMarketTypes(catalogue, exchange)) {
                    cataloguesByMarketId.putIfAbsent(catalogue.marketId(), catalogue);
                }
            }
            from = to;
        }
        return List.copyOf(cataloguesByMarketId.values());
    }

    private List<BetfairMarketCatalogue> listCataloguesInWindow(
        BetfairSession session,
        ExchangeConfig exchange,
        Instant from,
        Instant to
    ) {
        var marketData = exchange.marketData();
        try {
        return gateway.listMarketCatalogue(
            session,
            new BetfairMarketQuery(
                marketData.eventTypeIds(),
                List.of(),
                List.of(),
                BETFAIR_WINDOW_CATALOGUE_RESULTS,
                from,
                to
                )
            );
        } catch (RuntimeException exc) {
            Duration window = Duration.between(from, to);
            if (isTooMuchData(exc) && window.compareTo(MIN_FULL_SCAN_WINDOW) > 0) {
                Instant middle = from.plus(window.dividedBy(2));
                List<BetfairMarketCatalogue> catalogues = new ArrayList<>();
                catalogues.addAll(listCataloguesInWindow(session, exchange, from, middle));
                catalogues.addAll(listCataloguesInWindow(session, exchange, middle, to));
                return catalogues;
            }
            throw exc;
        }
    }

    private boolean isTooMuchData(RuntimeException exc) {
        return exc.getMessage() != null && exc.getMessage().contains("TOO_MUCH_DATA");
    }

    private boolean matchesConfiguredMarketTypes(BetfairMarketCatalogue catalogue, ExchangeConfig exchange) {
        if (exchange.marketData().marketTypeCodes().contains("MATCH_ODDS")) {
            return "Match Odds".equalsIgnoreCase(catalogue.marketName());
        }
        return true;
    }

    private int countEvents(List<BetfairMarketCatalogue> catalogues) {
        return (int) catalogues.stream()
            .map(BetfairMarketCatalogue::eventName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .count();
    }

    private int countTestEvents(List<BetfairMarketCatalogue> catalogues) {
        return (int) catalogues.stream()
            .map(BetfairMarketCatalogue::eventName)
            .filter(name -> name != null && name.toLowerCase(Locale.ROOT).contains("test"))
            .distinct()
            .count();
    }
}
