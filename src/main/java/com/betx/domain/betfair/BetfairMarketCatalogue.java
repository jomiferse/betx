package com.betx.domain.betfair;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record BetfairMarketCatalogue(
    String marketId,
    String marketName,
    String eventName,
    String competitionName,
    Instant marketStartTime,
    Map<Long, String> runnerNames
) {
    public BetfairMarketCatalogue {
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId is required.");
        }
        if (marketName == null || marketName.isBlank()) {
            throw new IllegalArgumentException("marketName is required.");
        }
        runnerNames = runnerNames == null ? Map.of() : Map.copyOf(runnerNames);
    }

    public BetfairMarketCatalogue(
        String marketId,
        String marketName,
        String eventName,
        String competitionName,
        Instant marketStartTime
    ) {
        this(marketId, marketName, eventName, competitionName, marketStartTime, Map.of());
    }

    public Optional<String> runnerName(long selectionId) {
        return Optional.ofNullable(runnerNames.get(selectionId));
    }
}
