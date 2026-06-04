package com.betx.application;

/** Audit entry for a Telegram alert candidate that was not sent. */
record TelegramBetAlertSkip(TelegramBetAlertCandidate candidate, String reason) {
    TelegramBetAlertSkip {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required.");
        }
        reason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }
}
