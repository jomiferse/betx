package com.betx.application;

import com.betx.application.DiagnosticsModel.MatchStatus;
import com.betx.application.DiagnosticsModel.RealOddsSource;
import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticsMatch(
    MatchStatus matchStatus,
    String eventName,
    String marketId,
    Long selectionId,
    String runnerName,
    String selectionSide,
    String competitionName,
    String strategyName,
    Instant recommendationTimestamp,
    Instant paperExecutionTimestamp,
    Instant realRecordedTimestamp,
    BigDecimal recommendedOdds,
    BigDecimal paperOdds,
    BigDecimal realRecordedOdds,
    RealOddsSource realOddsSource,
    BigDecimal closingOdds,
    BigDecimal paperStake,
    BigDecimal realStake,
    String paperResult,
    String realResult,
    BigDecimal paperPnl,
    BigDecimal realPnl,
    BigDecimal executionPnlDifference,
    BigDecimal paperPnlPerUnitStake,
    BigDecimal realPnlPerUnitStake,
    BigDecimal normalizedExecutionDifference
) {
}
