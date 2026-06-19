package com.betx.application;

import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Shared immutable DTOs for the read-only diagnostics use case. */
public final class DiagnosticsModel {
    private DiagnosticsModel() {
    }

    public enum MatchStatus {
        MATCHED,
        REAL_ONLY,
        PAPER_ONLY,
        AMBIGUOUS
    }

    public enum DiagnosticsDataProvenance {
        SQLITE_EXACT,
        LOG_CORRELATED,
        APPROXIMATED,
        UNAVAILABLE
    }

    public enum DiagnosticFindingSeverity {
        INFO,
        WARNING,
        ERROR
    }

    public enum RealOddsSource {
        BET_INTENT
    }

    public record DiagnosticsRequest(
        ConfigPath configPath,
        Instant from,
        Instant to,
        Path logsDir,
        Duration matchWindow
    ) {
        public DiagnosticsRequest {
            logsDir = logsDir == null ? Path.of("logs", "events") : logsDir;
            matchWindow = matchWindow == null || matchWindow.isNegative() || matchWindow.isZero()
                ? Duration.ofHours(24)
                : matchWindow;
        }
    }

    public record DiagnosticsDataset(
        List<RealBetDiagnosticRow> realBets,
        List<PaperTrade> paperTrades,
        long marketsScanned,
        long runnersAnalyzed,
        Map<String, Long> signalRecommendations,
        Map<String, Long> rejectionReasons
    ) {
        public DiagnosticsDataset {
            realBets = realBets == null ? List.of() : List.copyOf(realBets);
            paperTrades = paperTrades == null ? List.of() : List.copyOf(paperTrades);
            signalRecommendations = signalRecommendations == null ? Map.of() : Map.copyOf(signalRecommendations);
            rejectionReasons = rejectionReasons == null ? Map.of() : Map.copyOf(rejectionReasons);
        }
    }

    public record RealBetDiagnosticRow(
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
        BigDecimal recordedOdds,
        BigDecimal selectedStake,
        BetIntentStage stage,
        BetSettlementResult settlementResult,
        BigDecimal realizedProfitLoss,
        String externalOrderId,
        Instant createdAt,
        Instant settledAt,
        Instant updatedAt,
        BigDecimal availableBalance,
        BigDecimal effectiveAvailableBalance,
        BigDecimal reservedBalance,
        Instant balanceSnapshotAt
    ) {
        public RealBetDiagnosticRow {
            exchange = blankToNull(exchange);
            marketId = blankToNull(marketId);
            eventName = blankToNull(eventName);
            marketName = blankToNull(marketName);
            runnerName = blankToDefault(runnerName, "N/A");
            selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
            competitionName = blankToDefault(competitionName, "N/A");
            strategyName = blankToDefault(strategyName, "N/A");
        }

        public boolean settledWithPnl() {
            return stage == BetIntentStage.SETTLED && settlementResult != null && realizedProfitLoss != null;
        }

        public Instant sortTimestamp() {
            if (createdAt != null) {
                return createdAt;
            }
            if (updatedAt != null) {
                return updatedAt;
            }
            return settledAt;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
