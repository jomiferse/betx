package com.betx.application;

import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;

/** Raw persisted real-betting row used by reporting calculations. */
public record RealBettingReportRow(
    String id,
    String exchange,
    String marketId,
    long selectionId,
    String eventName,
    String marketName,
    String runnerName,
    SelectionSide selectionSide,
    String competitionName,
    String strategyName,
    BigDecimal odds,
    BigDecimal selectedStake,
    BigDecimal availableBalance,
    BigDecimal effectiveAvailableBalance,
    Instant balanceSnapshotAt,
    BetSettlementResult settlementResult,
    BigDecimal realizedProfitLoss,
    BetIntentStage stage,
    Instant createdAt,
    Instant settledAt,
    Instant updatedAt
) {
    public RealBettingReportRow {
        id = blankToNull(id);
        exchange = blankToNull(exchange);
        marketId = blankToNull(marketId);
        eventName = blankToNull(eventName);
        marketName = blankToNull(marketName);
        runnerName = blankToDefault(runnerName, "N/A");
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        competitionName = blankToDefault(competitionName, "N/A");
        strategyName = blankToDefault(strategyName, "N/A");
    }

    public boolean isSettledForPerformance() {
        return stage == BetIntentStage.SETTLED && settlementResult != null && realizedProfitLoss != null;
    }

    public boolean isOpen() {
        return stage == BetIntentStage.EXECUTED && (settlementResult == null || realizedProfitLoss == null);
    }

    public Instant performanceTimestamp() {
        if (settledAt != null) {
            return settledAt;
        }
        if (updatedAt != null) {
            return updatedAt;
        }
        return createdAt;
    }

    public Instant balanceTimestamp() {
        if (balanceSnapshotAt != null) {
            return balanceSnapshotAt;
        }
        if (updatedAt != null) {
            return updatedAt;
        }
        return createdAt;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
