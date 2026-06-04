package com.betx.domain.signal;

import java.time.Instant;
import java.util.List;

/** Analysis for one market event and its runners. */
public record EventAnalysis(
    String exchange,
    String marketId,
    String eventName,
    String competitionName,
    Instant marketStartTime,
    List<RunnerAnalysis> runners
) {
    public EventAnalysis {
        runners = runners == null ? List.of() : List.copyOf(runners);
    }
}
