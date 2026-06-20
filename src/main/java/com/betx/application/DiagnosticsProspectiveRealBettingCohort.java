package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.math.BigDecimal;

/** Real betting metrics restricted to records carrying a prospective evaluation_id. */
public record DiagnosticsProspectiveRealBettingCohort(
    long realBets,
    long settledBets,
    long openBets,
    long wins,
    long losses,
    BigDecimal turnover,
    BigDecimal netRealizedPnl,
    BigDecimal roi,
    BigDecimal averageRequestedOdds,
    BigDecimal averageExecutedOdds,
    BigDecimal requestedToExecutedOddsDifference,
    long fullyMatched,
    long partiallyMatched,
    long unmatched,
    DiagnosticsDataProvenance provenance
) {
}
