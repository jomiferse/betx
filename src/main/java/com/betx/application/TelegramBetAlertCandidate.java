package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Read-only Telegram alert candidate derived from one runner analysis. */
record TelegramBetAlertCandidate(
    RunnerAnalysis analysis,
    Optional<MarketSnapshot> previousSnapshot,
    TelegramBetAlertTrigger trigger,
    BigDecimal triggerPercentageDelta
) {
    TelegramBetAlertCandidate {
        previousSnapshot = previousSnapshot == null ? Optional.empty() : previousSnapshot;
    }

    static TelegramBetAlertCandidate from(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return tryFrom(analysis, previousSnapshot)
            .orElseThrow(() -> new IllegalArgumentException("Telegram BET alerts require a favorable odds or liquidity trigger."));
    }

    static Optional<TelegramBetAlertCandidate> tryFrom(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return TelegramBetAlertTrigger.fromReason(analysis.reason())
            .map(trigger -> new TelegramBetAlertCandidate(
                analysis,
                previousSnapshot,
                trigger,
                triggerPercentageDelta(trigger, analysis, previousSnapshot)
            ));
    }

    String marketKey() {
        return analysis.exchange() + "|" + analysis.marketId();
    }

    String displayRunner() {
        String runner = analysis.displayRunner();
        return "The Draw".equalsIgnoreCase(runner) ? "Draw" : runner;
    }

    boolean isDraw() {
        return "draw".equalsIgnoreCase(displayRunner());
    }

    private static BigDecimal triggerPercentageDelta(
        TelegramBetAlertTrigger trigger,
        RunnerAnalysis analysis,
        Optional<MarketSnapshot> previousSnapshot
    ) {
        return previousSnapshot.map(previous -> switch (trigger) {
                case ODDS_MOVEMENT -> percentageDelta(previous.bestBackPrice(), analysis.bestBackPrice());
                case LIQUIDITY_MOVEMENT -> percentageDelta(previous.liquidity(), analysis.liquidity());
            })
            .orElse(null);
    }

    private static BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
