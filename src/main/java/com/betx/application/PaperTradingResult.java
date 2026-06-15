package com.betx.application;

import java.util.List;

/** Result of one read-only prospective paper-trading scan. */
public record PaperTradingResult(
    List<BacktestPaperTrade> paperTrades,
    List<String> failures,
    int runnersAnalyzed,
    int snapshotsSaved,
    int marketsScanned,
    int recommendationsGenerated,
    int duplicatesSkipped,
    int executionFailures,
    int missingClosingPrices,
    int unsettledMarkets,
    int settledTrades,
    PaperTradeHistoryDiagnostics historyDiagnostics
) {
    public PaperTradingResult {
        paperTrades = paperTrades == null ? List.of() : List.copyOf(paperTrades);
        failures = failures == null ? List.of() : List.copyOf(failures);
        historyDiagnostics = historyDiagnostics == null ? PaperTradeHistoryDiagnostics.empty() : historyDiagnostics;
    }

    public PaperTradingResult(
        List<BacktestPaperTrade> paperTrades,
        List<String> failures,
        int runnersAnalyzed,
        int snapshotsSaved,
        int marketsScanned,
        int recommendationsGenerated,
        int duplicatesSkipped,
        int executionFailures,
        int missingClosingPrices,
        int unsettledMarkets,
        int settledTrades
    ) {
        this(
            paperTrades,
            failures,
            runnersAnalyzed,
            snapshotsSaved,
            marketsScanned,
            recommendationsGenerated,
            duplicatesSkipped,
            executionFailures,
            missingClosingPrices,
            unsettledMarkets,
            settledTrades,
            PaperTradeHistoryDiagnostics.empty()
        );
    }

    public PaperTradingResult(
        List<BacktestPaperTrade> paperTrades,
        List<String> failures,
        int runnersAnalyzed,
        int snapshotsSaved
    ) {
        this(paperTrades, failures, runnersAnalyzed, snapshotsSaved, 0, paperTrades == null ? 0 : paperTrades.size(), 0, 0, 0, 0, 0);
    }
}
