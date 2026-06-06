package com.betx.domain.telegram;

import java.math.BigDecimal;
import java.time.Instant;

/** Persisted live bet confirmation state. */
public record TelegramBetIntent(
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
    TelegramBetIntentStage stage,
    Instant createdAt,
    Instant updatedAt
) {
    public TelegramBetIntent {
        id = id == null ? null : id.strip();
        exchange = exchange == null ? null : exchange.strip();
        marketId = marketId == null ? null : marketId.strip();
        eventName = eventName == null ? null : eventName.strip();
        marketName = marketName == null ? null : marketName.strip();
        runnerName = runnerName == null ? null : runnerName.strip();
        reason = reason == null ? null : reason.strip();
        resultMessage = resultMessage == null ? null : resultMessage.strip();
        if (stage == null) {
            stage = TelegramBetIntentStage.AWAITING_CONFIRMATION;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public String displayRunner() {
        return runnerName == null || runnerName.isBlank() ? String.valueOf(selectionId) : runnerName;
    }

    public TelegramBetIntent withStage(TelegramBetIntentStage newStage, BigDecimal balance, BigDecimal stake) {
        return withStage(newStage, balance, stake, resultMessage);
    }

    public TelegramBetIntent withStage(TelegramBetIntentStage newStage, BigDecimal balance, BigDecimal stake, String message) {
        return withStageAt(newStage, balance, stake, message, Instant.now());
    }

    public TelegramBetIntent withStageAt(
        TelegramBetIntentStage newStage,
        BigDecimal balance,
        BigDecimal stake,
        String message,
        Instant updatedAt
    ) {
        return new TelegramBetIntent(
            id,
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
            newStage,
            createdAt,
            updatedAt
        );
    }
}
