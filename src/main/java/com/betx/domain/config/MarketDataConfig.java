package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Market data polling and discovery settings. */
public record MarketDataConfig(
    @JsonProperty("poll_interval_seconds") Integer pollIntervalSeconds,
    @JsonProperty("max_markets") Integer maxMarkets,
    @JsonProperty("event_type_ids") List<String> eventTypeIds,
    @JsonProperty("market_type_codes") List<String> marketTypeCodes,
    @JsonProperty("scan_all_markets") Boolean scanAllMarkets,
    @JsonProperty("betfair_event_batch_size") Integer betfairEventBatchSize
) {
    public MarketDataConfig {
        pollIntervalSeconds = pollIntervalSeconds == null ? 60 : pollIntervalSeconds;
        maxMarkets = maxMarkets == null ? 0 : maxMarkets;
        eventTypeIds = normalize(eventTypeIds, List.of("1"));
        marketTypeCodes = normalize(marketTypeCodes, List.of("MATCH_ODDS"));
        scanAllMarkets = scanAllMarkets == null ? true : scanAllMarkets;
        betfairEventBatchSize = betfairEventBatchSize == null ? 50 : betfairEventBatchSize;
    }

    public MarketDataConfig(
        Integer pollIntervalSeconds,
        Integer maxMarkets,
        List<String> eventTypeIds,
        List<String> marketTypeCodes
    ) {
        this(pollIntervalSeconds, maxMarkets, eventTypeIds, marketTypeCodes, null, null);
    }

    private static List<String> normalize(List<String> values, List<String> defaults) {
        if (values == null) {
            return defaults;
        }
        List<String> normalized = values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .toList();
        return normalized.isEmpty() ? defaults : List.copyOf(normalized);
    }
}
