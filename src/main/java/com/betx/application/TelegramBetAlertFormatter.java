package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Formats dry-run BET recommendations for Telegram HTML alerts. */
public class TelegramBetAlertFormatter {
    private static final ZoneId ALERT_ZONE = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter KICKOFF_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z", Locale.ENGLISH)
        .withZone(ALERT_ZONE);

    public String format(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return TelegramBetAlertCandidate.tryFrom(analysis, previousSnapshot)
            .map(candidate -> format(candidate))
            .orElseGet(() -> legacyFormat(analysis, previousSnapshot));
    }

    String format(TelegramBetAlertCandidate candidate) {
        RunnerAnalysis analysis = candidate.analysis();
        return "<b>BETX DRY-RUN SIGNAL</b>\n\n"
            + "Trigger: " + escape(candidate.trigger().displayLabel() + " " + triggerDelta(candidate)) + "\n"
            + "<b>" + escape(textOrDefault(analysis.eventName(), "unknown event")) + "</b>\n"
            + "Runner: " + escape(candidate.displayRunner()) + "\n"
            + "Side: BACK\n\n"
            + "Odds: " + odds(analysis, candidate.previousSnapshot()) + "\n"
            + "Lay: " + numeric(analysis.bestLayPrice()) + "\n"
            + "Spread: " + numeric(analysis.spread()) + "\n"
            + "Liquidity: " + numeric(analysis.liquidity()) + "\n\n"
            + "Kickoff: " + kickoff(analysis) + "\n"
            + "Market: " + escape(textOrDefault(analysis.marketName(), "n/a")) + "\n"
            + "Exchange: " + escape(textOrDefault(analysis.exchange(), "n/a")) + "\n"
            + "Market ID: " + escape(textOrDefault(analysis.marketId(), "n/a")) + "\n"
            + "Selection ID: " + analysis.selectionId() + "\n\n"
            + "Why: " + escape(reason(analysis.reason(), Optional.of(candidate.trigger()))) + "\n"
            + "Status: DRY-RUN ONLY. No real bet placed.";
    }

    private String legacyFormat(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return "<b>BETX DRY-RUN SIGNAL</b>\n\n"
            + "<b>" + escape(textOrDefault(analysis.eventName(), "unknown event")) + "</b>\n"
            + "Runner: " + escape(displayRunner(analysis)) + "\n"
            + "Side: BACK\n\n"
            + "Odds: " + odds(analysis, previousSnapshot) + "\n"
            + "Lay: " + numeric(analysis.bestLayPrice()) + "\n"
            + "Spread: " + numeric(analysis.spread()) + "\n"
            + "Liquidity: " + numeric(analysis.liquidity()) + "\n\n"
            + "Kickoff: " + kickoff(analysis) + "\n"
            + "Market: " + escape(textOrDefault(analysis.marketName(), "n/a")) + "\n"
            + "Exchange: " + escape(textOrDefault(analysis.exchange(), "n/a")) + "\n"
            + "Market ID: " + escape(textOrDefault(analysis.marketId(), "n/a")) + "\n"
            + "Selection ID: " + analysis.selectionId() + "\n\n"
            + "Why: " + escape(reason(analysis.reason(), Optional.empty())) + "\n"
            + "Status: DRY-RUN ONLY. No real bet placed.";
    }

    private String odds(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        if (analysis.bestBackPrice() == null) {
            return "n/a";
        }

        Optional<BigDecimal> previousBack = previousSnapshot.map(MarketSnapshot::bestBackPrice);
        if (previousBack.isEmpty()) {
            return "current back " + numeric(analysis.bestBackPrice());
        }

        BigDecimal percentageDelta = percentageDelta(previousBack.get(), analysis.bestBackPrice());
        if (percentageDelta == null) {
            return "current back " + numeric(analysis.bestBackPrice());
        }

        return numeric(previousBack.get())
            + " -> current back "
            + numeric(analysis.bestBackPrice())
            + " ("
            + signedPercent(percentageDelta)
            + ")";
    }

    private String kickoff(RunnerAnalysis analysis) {
        return analysis.marketStartTime() == null ? "n/a" : KICKOFF_FORMATTER.format(analysis.marketStartTime());
    }

    private String reason(String reason, Optional<TelegramBetAlertTrigger> trigger) {
        String triggerToken = trigger.map(TelegramBetAlertTrigger::reasonToken).orElse(null);
        String normalized = Arrays.stream(reason.split(","))
            .map(String::strip)
            .filter(value -> !value.isBlank())
            .filter(value -> !"dry_run_only".equals(value))
            .filter(value -> triggerToken == null || !triggerToken.equals(value))
            .map(value -> value.replace('_', ' '))
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
        return normalized.isBlank() ? "n/a" : normalized;
    }

    private String displayRunner(RunnerAnalysis analysis) {
        String runner = analysis.displayRunner();
        return "The Draw".equalsIgnoreCase(runner) ? "Draw" : runner;
    }

    private String triggerDelta(TelegramBetAlertCandidate candidate) {
        BigDecimal value = candidate.triggerPercentageDelta();
        return value == null ? "n/a" : signedPercent(value);
    }

    private String numeric(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return String.format(Locale.US, "%,.2f", value);
    }

    private String signedPercent(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + numeric(value) + "%";
    }

    private BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
