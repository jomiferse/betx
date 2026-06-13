package com.betx.domain.order;

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
            reason,
            odds,
            maxStake,
            availableBalance,
            selectedStake,
            resultMessage,
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
            reason,
            odds,
            maxStake,
            availableBalance,
            selectedStake,
            resultMessage,
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
            reason,
            odds,
            maxStake,
            availableBalance,
            selectedStake,
            resultMessage,
            externalOrderId,
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
            reason,
            odds,
            maxStake,
            balance,
            stake,
            message,
            externalOrderId,
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
            reason,
            odds,
            maxStake,
            availableBalance,
            selectedStake,
            resultMessage,
            newExternalOrderId,
            stage,
            createdAt,
            updatedAt
        );
    }
}
