package com.betx.application;

import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.order.BetExecutionStatus;
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
        LEGACY_APPROXIMATION,
        APPROXIMATED,
        UNAVAILABLE
    }

    public enum MatchGapReason {
        NO_PAPER_WITH_SAME_MARKET_SELECTION,
        NO_REAL_WITH_SAME_MARKET_SELECTION,
        OUTSIDE_MATCH_WINDOW,
        MULTIPLE_PAPER_CANDIDATES,
        MULTIPLE_REAL_CANDIDATES,
        MULTIPLE_VALID_CANDIDATES,
        MISSING_CORRELATION_FIELDS,
        MISSING_TIMESTAMP,
        DIFFERENT_EXCHANGE,
        DIRECT_RECOMMENDATION_ID_MISMATCH,
        UNKNOWN
    }

    public enum MatchProvenance {
        DIRECT_RECOMMENDATION_ID,
        LEGACY_MARKET_SELECTION_TIME,
        UNMATCHED,
        AMBIGUOUS
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
        Map<String, Long> rejectionReasons,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsPaperRecommendationCoverage paperRecommendationCoverage
    ) {
        public DiagnosticsDataset(
            List<RealBetDiagnosticRow> realBets,
            List<PaperTrade> paperTrades,
            long marketsScanned,
            long runnersAnalyzed,
            Map<String, Long> signalRecommendations,
            Map<String, Long> rejectionReasons
        ) {
            this(
                realBets,
                paperTrades,
                marketsScanned,
                runnersAnalyzed,
                signalRecommendations,
                rejectionReasons,
                DiagnosticsBetRecommendationsSummary.empty(),
                DiagnosticsPaperRecommendationCoverage.empty()
            );
        }

        public DiagnosticsDataset(
            List<RealBetDiagnosticRow> realBets,
            List<PaperTrade> paperTrades,
            long marketsScanned,
            long runnersAnalyzed,
            Map<String, Long> signalRecommendations,
            Map<String, Long> rejectionReasons,
            DiagnosticsBetRecommendationsSummary betRecommendations
        ) {
            this(
                realBets,
                paperTrades,
                marketsScanned,
                runnersAnalyzed,
                signalRecommendations,
                rejectionReasons,
                betRecommendations,
                DiagnosticsPaperRecommendationCoverage.empty()
            );
        }

        public DiagnosticsDataset {
            realBets = realBets == null ? List.of() : List.copyOf(realBets);
            paperTrades = paperTrades == null ? List.of() : List.copyOf(paperTrades);
            signalRecommendations = signalRecommendations == null ? Map.of() : Map.copyOf(signalRecommendations);
            rejectionReasons = rejectionReasons == null ? Map.of() : Map.copyOf(rejectionReasons);
            betRecommendations = betRecommendations == null ? DiagnosticsBetRecommendationsSummary.empty() : betRecommendations;
            paperRecommendationCoverage = paperRecommendationCoverage == null
                ? DiagnosticsPaperRecommendationCoverage.empty()
                : paperRecommendationCoverage;
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
        Instant balanceSnapshotAt,
        String evaluationId,
        String recommendationId,
        Instant recommendedAt,
        BigDecimal recommendedOdds,
        Instant orderSubmittedAt,
        Instant orderResponseAt,
        Instant orderAcceptedAt,
        Instant executedAt,
        BigDecimal requestedOdds,
        BigDecimal averageExecutedOdds,
        BigDecimal requestedStake,
        BigDecimal matchedStake,
        BigDecimal remainingStake,
        BetExecutionStatus executionStatus
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

        public RealBetDiagnosticRow(
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
            this(
                id,
                exchange,
                marketId,
                selectionId,
                eventName,
                marketName,
                runnerName,
                selectionSide,
                competitionName,
                strategyName,
                recordedOdds,
                selectedStake,
                stage,
                settlementResult,
                realizedProfitLoss,
                externalOrderId,
                createdAt,
                settledAt,
                updatedAt,
                availableBalance,
                effectiveAvailableBalance,
                reservedBalance,
                balanceSnapshotAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
