package com.betx.domain.betfair;

import java.time.Instant;

/** Betfair event metadata returned by market discovery. */
public record BetfairEvent(
    String id,
    String name,
    String countryCode,
    String timezone,
    Instant openDate,
    int marketCount
) {
}
