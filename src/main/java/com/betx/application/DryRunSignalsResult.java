package com.betx.application;

import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.RunnerAnalysis;
import java.util.List;

/** Result of one multi-exchange signal cycle. */
public record DryRunSignalsResult(
    List<BetSignal> signals,
    List<String> failures,
    boolean noEnabledExchanges,
    int snapshotsSaved,
    int comparisonsCalculated,
    List<MarketSnapshotChange> changes,
    List<RunnerAnalysis> runnerAnalyses,
    int marketsRead,
    int ignoredMarkets,
    int eventsRead,
    int ignoredEvents
) {
    public DryRunSignalsResult {
        signals = signals == null ? List.of() : List.copyOf(signals);
        failures = failures == null ? List.of() : List.copyOf(failures);
        changes = changes == null ? List.of() : List.copyOf(changes);
        runnerAnalyses = runnerAnalyses == null ? List.of() : List.copyOf(runnerAnalyses);
    }

    public DryRunSignalsResult(List<BetSignal> signals, List<String> failures, boolean noEnabledExchanges) {
        this(signals, failures, noEnabledExchanges, 0, 0, List.of(), List.of(), 0, 0, 0, 0);
    }
}
