package com.betx.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Per-cycle snapshot-history and analyzer diagnostics for paper trading. */
public record PaperTradeHistoryDiagnostics(
    int previousSnapshotsLoaded,
    int runnersWithoutPreviousSnapshot,
    int runnersWithPreviousSnapshot,
    int runnersWithSufficientHistory,
    int runnersWithChangedOdds,
    int runnersWithUnchangedOdds,
    Instant oldestPreviousSnapshot,
    Instant newestPreviousSnapshot,
    int stableMarketKeys,
    int stableSelectionKeys,
    List<PaperTradeRunnerClassificationDiagnostic> runnerClassificationSample,
    List<String> warnings,
    Map<PaperTradeAnalyzerRejectionReason, Integer> analyzerRejectionCounts
) {
    public PaperTradeHistoryDiagnostics {
        runnerClassificationSample = runnerClassificationSample == null ? List.of() : List.copyOf(runnerClassificationSample);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        analyzerRejectionCounts = completeCounts(analyzerRejectionCounts);
    }

    public static PaperTradeHistoryDiagnostics empty() {
        return new PaperTradeHistoryDiagnostics(
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            0,
            0,
            List.of(),
            List.of(),
            Map.of()
        );
    }

    private static Map<PaperTradeAnalyzerRejectionReason, Integer> completeCounts(
        Map<PaperTradeAnalyzerRejectionReason, Integer> counts
    ) {
        EnumMap<PaperTradeAnalyzerRejectionReason, Integer> complete = new EnumMap<>(PaperTradeAnalyzerRejectionReason.class);
        for (PaperTradeAnalyzerRejectionReason reason : PaperTradeAnalyzerRejectionReason.values()) {
            complete.put(reason, 0);
        }
        if (counts != null) {
            counts.forEach((reason, count) -> complete.put(reason, count == null ? 0 : count));
        }
        return Map.copyOf(complete);
    }
}
