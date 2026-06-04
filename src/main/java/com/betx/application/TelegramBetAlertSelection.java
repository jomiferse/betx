package com.betx.application;

import java.util.List;

/** Result of applying the Telegram BET alert policy to one cycle. */
record TelegramBetAlertSelection(List<TelegramBetAlertCandidate> alertsToSend, List<TelegramBetAlertSkip> skippedAlerts) {
    TelegramBetAlertSelection {
        alertsToSend = alertsToSend == null ? List.of() : List.copyOf(alertsToSend);
        skippedAlerts = skippedAlerts == null ? List.of() : List.copyOf(skippedAlerts);
    }
}
