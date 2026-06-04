package com.betx.domain.betfair;

import java.util.List;
import java.time.Instant;

public record BetfairMarketQuery(
    List<String> eventTypeIds,
    List<String> eventIds,
    List<String> marketTypeCodes,
    int maxResults,
    Instant marketStartTimeFrom,
    Instant marketStartTimeTo
) {
    public BetfairMarketQuery {
        eventTypeIds = eventTypeIds == null ? List.of() : List.copyOf(eventTypeIds);
        eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
        marketTypeCodes = marketTypeCodes == null ? List.of() : List.copyOf(marketTypeCodes);
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than zero.");
        }
    }

    public BetfairMarketQuery(List<String> eventTypeIds, List<String> marketTypeCodes, int maxResults) {
        this(eventTypeIds, List.of(), marketTypeCodes, maxResults);
    }

    public BetfairMarketQuery(List<String> eventTypeIds, List<String> eventIds, List<String> marketTypeCodes, int maxResults) {
        this(eventTypeIds, eventIds, marketTypeCodes, maxResults, null, null);
    }
}
