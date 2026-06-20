package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Durable paper-mode trade record, kept separate from real order execution. */
public record PaperTrade(
    String id,
    String exchange,
    String marketId,
    long selectionId,
    String eventName,
    String marketName,
    String league,
    Instant marketStartTime,
    String runnerName,
    BetSide side,
    PaperTradeStatus status,
    Instant recommendationTimestamp,
    BigDecimal availableBackOdds,
    BigDecimal requestedOdds,
    Instant executionTimestamp,
    BigDecimal executionOdds,
    boolean matched,
    Instant closingTimestamp,
    BigDecimal closingOdds,
    Instant settlementTimestamp,
    BacktestOutcome result,
    BigDecimal stake,
    BigDecimal grossPnl,
    BigDecimal commission,
    BigDecimal netPnl,
    BigDecimal decimalClvRatio,
    BigDecimal impliedProbabilityChange,
    boolean paperMode,
    String recommendationId
) {
    public PaperTrade {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("exchange is required.");
        }
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId is required.");
        }
        if (selectionId <= 0) {
            throw new IllegalArgumentException("selectionId must be greater than zero.");
        }
        id = id == null || id.isBlank() ? key(exchange, marketId, selectionId) : id;
        league = league == null || league.isBlank() ? "unknown" : league;
        runnerName = runnerName == null || runnerName.isBlank() ? "unknown" : runnerName;
        side = side == null ? BetSide.BACK : side;
        status = status == null ? PaperTradeStatus.RECOMMENDED : status;
        stake = stake == null ? BigDecimal.valueOf(5) : stake;
        grossPnl = grossPnl == null ? BigDecimal.ZERO : grossPnl;
        commission = commission == null ? BigDecimal.ZERO : commission;
        netPnl = netPnl == null ? BigDecimal.ZERO : netPnl;
        recommendationId = recommendationId == null || recommendationId.isBlank() ? null : recommendationId.strip();
    }

    public PaperTrade(
        String id,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String league,
        Instant marketStartTime,
        String runnerName,
        BetSide side,
        PaperTradeStatus status,
        Instant recommendationTimestamp,
        BigDecimal availableBackOdds,
        BigDecimal requestedOdds,
        Instant executionTimestamp,
        BigDecimal executionOdds,
        boolean matched,
        Instant closingTimestamp,
        BigDecimal closingOdds,
        Instant settlementTimestamp,
        BacktestOutcome result,
        BigDecimal stake,
        BigDecimal grossPnl,
        BigDecimal commission,
        BigDecimal netPnl,
        BigDecimal decimalClvRatio,
        BigDecimal impliedProbabilityChange,
        boolean paperMode
    ) {
        this(
            id,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            league,
            marketStartTime,
            runnerName,
            side,
            status,
            recommendationTimestamp,
            availableBackOdds,
            requestedOdds,
            executionTimestamp,
            executionOdds,
            matched,
            closingTimestamp,
            closingOdds,
            settlementTimestamp,
            result,
            stake,
            grossPnl,
            commission,
            netPnl,
            decimalClvRatio,
            impliedProbabilityChange,
            paperMode,
            null
        );
    }

    public static PaperTrade recommended(MarketSnapshot snapshot, Instant observedAt, BigDecimal stake) {
        return new PaperTrade(
            key(snapshot.exchange(), snapshot.marketId(), snapshot.selectionId()),
            snapshot.exchange(),
            snapshot.marketId(),
            snapshot.selectionId(),
            snapshot.eventName(),
            snapshot.marketName(),
            snapshot.competitionName(),
            snapshot.marketStartTime(),
            snapshot.runnerName(),
            BetSide.BACK,
            PaperTradeStatus.RECOMMENDED,
            observedAt,
            snapshot.bestBackPrice(),
            snapshot.bestBackPrice(),
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            stake,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            true
        );
    }

    public PaperTrade withExecuted(Instant timestamp, BigDecimal odds, boolean matched) {
        return new PaperTrade(
            id,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            league,
            marketStartTime,
            runnerName,
            side,
            matched ? PaperTradeStatus.EXECUTED : PaperTradeStatus.EXECUTION_FAILED,
            recommendationTimestamp,
            availableBackOdds,
            requestedOdds,
            timestamp,
            odds,
            matched,
            closingTimestamp,
            closingOdds,
            settlementTimestamp,
            result,
            stake,
            grossPnl,
            commission,
            netPnl,
            decimalClvRatio,
            impliedProbabilityChange,
            true
        );
    }

    public PaperTrade withClosed(Instant timestamp, BigDecimal odds) {
        BigDecimal clv = BacktestPaperTrade.clvRatio(executionOdds, odds);
        BigDecimal impliedProbability = BacktestPaperTrade.impliedProbabilityChange(executionOdds, odds);
        return new PaperTrade(
            id,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            league,
            marketStartTime,
            runnerName,
            side,
            PaperTradeStatus.CLOSED,
            recommendationTimestamp,
            availableBackOdds,
            requestedOdds,
            executionTimestamp,
            executionOdds,
            matched,
            timestamp,
            odds,
            settlementTimestamp,
            result,
            stake,
            grossPnl,
            commission,
            netPnl,
            clv,
            impliedProbability,
            true
        );
    }

    public PaperTrade withSettled(Instant timestamp, BacktestOutcome outcome, BigDecimal commissionRate) {
        BigDecimal gross = outcome == BacktestOutcome.WIN
            ? stake.multiply(executionOdds.subtract(BigDecimal.ONE))
            : stake.negate();
        BigDecimal paidCommission = gross.max(BigDecimal.ZERO)
            .multiply(commissionRate == null ? BigDecimal.ZERO : commissionRate)
            .setScale(2, RoundingMode.HALF_UP);
        return new PaperTrade(
            id,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            league,
            marketStartTime,
            runnerName,
            side,
            PaperTradeStatus.SETTLED,
            recommendationTimestamp,
            availableBackOdds,
            requestedOdds,
            executionTimestamp,
            executionOdds,
            matched,
            closingTimestamp,
            closingOdds,
            timestamp,
            outcome,
            stake,
            gross.setScale(2, RoundingMode.HALF_UP),
            paidCommission,
            gross.subtract(paidCommission).setScale(2, RoundingMode.HALF_UP),
            decimalClvRatio,
            impliedProbabilityChange,
            true
        );
    }

    public BacktestPaperTrade toBacktestPaperTrade() {
        return new BacktestPaperTrade(
            marketId,
            marketId,
            league,
            "prospective",
            eventName,
            runnerName,
            side,
            recommendationTimestamp,
            executionTimestamp,
            closingTimestamp,
            availableBackOdds,
            requestedOdds,
            executionOdds,
            closingOdds,
            result,
            grossPnl,
            commission,
            netPnl,
            decimalClvRatio,
            impliedProbabilityChange,
            status.name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    public static String key(String exchange, String marketId, long selectionId) {
        return exchange + "|" + marketId + "|" + selectionId;
    }
}
