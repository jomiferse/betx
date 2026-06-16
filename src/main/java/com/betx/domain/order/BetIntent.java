package com.betx.domain.order;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;

/** Persisted live bet execution intent, regardless of the trigger channel. */
public record BetIntent(
    String id,
    BetIntentSource source,
    String exchange,
    String marketId,
    long selectionId,
    String eventName,
    String marketName,
    String runnerName,
    BetSide side,
    String reason,
    BigDecimal odds,
    BigDecimal maxStake,
    BigDecimal availableBalance,
    BigDecimal effectiveAvailableBalance,
    BigDecimal reservedBalance,
    Instant balanceSnapshotAt,
    BigDecimal selectedStake,
    String resultMessage,
    String externalOrderId,
    Instant settledAt,
    BetSettlementResult settlementResult,
    BigDecimal realizedProfitLoss,
    BetIntentStage stage,
    Instant createdAt,
    Instant updatedAt
) {
    public BetIntent {
        id = id == null ? null : id.strip();
        if (source == null) {
            source = BetIntentSource.TELEGRAM_CONFIRMATION;
        }
        exchange = exchange == null ? null : exchange.strip();
        marketId = marketId == null ? null : marketId.strip();
        eventName = eventName == null ? null : eventName.strip();
        marketName = marketName == null ? null : marketName.strip();
        runnerName = runnerName == null ? null : runnerName.strip();
        if (side == null) {
            side = BetSide.BACK;
        }
        reason = reason == null ? null : reason.strip();
        resultMessage = resultMessage == null ? null : resultMessage.strip();
        externalOrderId = externalOrderId == null ? null : externalOrderId.strip();
        if (stage == null) {
            stage = BetIntentStage.AWAITING_CONFIRMATION;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public BetIntent(
        String id,
        BetIntentSource source,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String runnerName,
        String reason,
        BigDecimal odds,
        BigDecimal maxStake,
        BigDecimal availableBalance,
        BigDecimal selectedStake,
        String resultMessage,
        BetIntentStage stage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            BetSide.BACK,
            reason,
            odds,
            maxStake,
            availableBalance,
            null,
            null,
            null,
            selectedStake,
            resultMessage,
            null,
            null,
            null,
            null,
            stage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent(
        String id,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String runnerName,
        String reason,
        BigDecimal odds,
        BigDecimal maxStake,
        BigDecimal availableBalance,
        BigDecimal selectedStake,
        String resultMessage,
        BetIntentStage stage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            BetIntentSource.TELEGRAM_CONFIRMATION,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            BetSide.BACK,
            reason,
            odds,
            maxStake,
            availableBalance,
            null,
            null,
            null,
            selectedStake,
            resultMessage,
            null,
            null,
            null,
            null,
            stage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent(
        String id,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String runnerName,
        String reason,
        BigDecimal odds,
        BigDecimal maxStake,
        BigDecimal availableBalance,
        BigDecimal selectedStake,
        String resultMessage,
        String externalOrderId,
        BetIntentStage stage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            BetIntentSource.TELEGRAM_CONFIRMATION,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            BetSide.BACK,
            reason,
            odds,
            maxStake,
            availableBalance,
            null,
            null,
            null,
            selectedStake,
            resultMessage,
            externalOrderId,
            null,
            null,
            null,
            stage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent(
        String id,
        BetIntentSource source,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String runnerName,
        String reason,
        BigDecimal odds,
        BigDecimal maxStake,
        BigDecimal availableBalance,
        BigDecimal selectedStake,
        String resultMessage,
        String externalOrderId,
        BetIntentStage stage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            BetSide.BACK,
            reason,
            odds,
            maxStake,
            availableBalance,
            null,
            null,
            null,
            selectedStake,
            resultMessage,
            externalOrderId,
            null,
            null,
            null,
            stage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent(
        String id,
        BetIntentSource source,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String marketName,
        String runnerName,
        String reason,
        BigDecimal odds,
        BigDecimal maxStake,
        BigDecimal availableBalance,
        BigDecimal effectiveAvailableBalance,
        BigDecimal reservedBalance,
        Instant balanceSnapshotAt,
        BigDecimal selectedStake,
        String resultMessage,
        String externalOrderId,
        BetIntentStage stage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            BetSide.BACK,
            reason,
            odds,
            maxStake,
            availableBalance,
            effectiveAvailableBalance,
            reservedBalance,
            balanceSnapshotAt,
            selectedStake,
            resultMessage,
            externalOrderId,
            null,
            null,
            null,
            stage,
            createdAt,
            updatedAt
        );
    }

    public String displayRunner() {
        return runnerName == null || runnerName.isBlank() ? String.valueOf(selectionId) : runnerName;
    }

    public BetIntent withStage(BetIntentStage newStage, BigDecimal balance, BigDecimal stake) {
        return withStage(newStage, balance, stake, resultMessage);
    }

    public BetIntent withStage(BetIntentStage newStage, BigDecimal balance, BigDecimal stake, String message) {
        return withStageAt(newStage, balance, stake, message, Instant.now());
    }

    public BetIntent withStageAt(
        BetIntentStage newStage,
        BigDecimal balance,
        BigDecimal stake,
        String message,
        Instant updatedAt
    ) {
        return new BetIntent(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            side,
            reason,
            odds,
            maxStake,
            balance,
            effectiveAvailableBalance,
            reservedBalance,
            balanceSnapshotAt,
            stake,
            message,
            externalOrderId,
            settledAt,
            settlementResult,
            realizedProfitLoss,
            newStage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent withExternalOrderId(String newExternalOrderId) {
        return new BetIntent(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            side,
            reason,
            odds,
            maxStake,
            availableBalance,
            effectiveAvailableBalance,
            reservedBalance,
            balanceSnapshotAt,
            selectedStake,
            resultMessage,
            newExternalOrderId,
            settledAt,
            settlementResult,
            realizedProfitLoss,
            stage,
            createdAt,
            updatedAt
        );
    }

    public BetIntent withSettlement(
        BetIntentStage newStage,
        BetSettlementResult newSettlementResult,
        BigDecimal newRealizedProfitLoss,
        Instant newSettledAt,
        String message
    ) {
        return new BetIntent(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            side,
            reason,
            odds,
            maxStake,
            availableBalance,
            effectiveAvailableBalance,
            reservedBalance,
            balanceSnapshotAt,
            selectedStake,
            message,
            externalOrderId,
            newSettledAt,
            newSettlementResult,
            newRealizedProfitLoss,
            newStage,
            createdAt,
            newSettledAt == null ? Instant.now() : newSettledAt
        );
    }

    public BetIntent withExecutionBalanceAudit(
        BigDecimal newEffectiveAvailableBalance,
        BigDecimal newReservedBalance,
        Instant newBalanceSnapshotAt
    ) {
        return new BetIntent(
            id,
            source,
            exchange,
            marketId,
            selectionId,
            eventName,
            marketName,
            runnerName,
            side,
            reason,
            odds,
            maxStake,
            availableBalance,
            newEffectiveAvailableBalance,
            newReservedBalance,
            newBalanceSnapshotAt,
            selectedStake,
            resultMessage,
            externalOrderId,
            settledAt,
            settlementResult,
            realizedProfitLoss,
            stage,
            createdAt,
            updatedAt
        );
    }
}
