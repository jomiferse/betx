package com.betx.application;

import java.util.Arrays;
import java.util.Optional;

/** Trigger that made a dry-run BET actionable for Telegram. */
enum TelegramBetAlertTrigger {
    ODDS_MOVEMENT("odds movement", "odds_movement", "favorable_odds_movement"),
    LIQUIDITY_MOVEMENT("liquidity movement", "liquidity_movement", "favorable_liquidity_movement");

    private final String displayLabel;
    private final String logLabel;
    private final String reasonToken;

    TelegramBetAlertTrigger(String displayLabel, String logLabel, String reasonToken) {
        this.displayLabel = displayLabel;
        this.logLabel = logLabel;
        this.reasonToken = reasonToken;
    }

    String displayLabel() {
        return displayLabel;
    }

    String logLabel() {
        return logLabel;
    }

    String reasonToken() {
        return reasonToken;
    }

    static Optional<TelegramBetAlertTrigger> fromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(trigger -> reason.contains(trigger.reasonToken))
            .findFirst();
    }
}
