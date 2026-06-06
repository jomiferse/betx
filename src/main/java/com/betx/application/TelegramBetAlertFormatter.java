package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        return "<b>BETX SIGNAL</b>\n"
            + "DRY-RUN ONLY\n\n"
            + "⚽ Market movement detected\n"
            + "Score: " + analysis.score().value() + "/100 " + confidence(analysis) + "\n\n"
            + "Trigger: " + escape(TelegramMessageFormat.triggerLabel(candidate.trigger()) + " (" + triggerDelta(candidate) + ")") + "\n\n"
            + "<b>" + escape(displayEventName(analysis.eventName())) + "</b>\n"
            + TelegramMessageFormat.selectionLine(candidate.displayRunner(), analysis.bestBackPrice()) + "\n"
            + TelegramMessageFormat.actionLine(analysis.exchange()) + "\n\n"
            + previousOdds(analysis, candidate.previousSnapshot()) + "\n"
            + "Kickoff: " + kickoff(analysis) + "\n"
            + "Market: " + escape(textOrDefault(analysis.marketName(), "n/a")) + "\n"
            + "\n"
            + "Why this signal:\n"
            + scoreReasonLines(analysis) + "\n\n"
            + "Safety:\n"
            + "DRY-RUN ONLY. No real bet placed.";
    }

    private String legacyFormat(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return "<b>BETX SIGNAL</b>\n"
            + "DRY-RUN ONLY\n\n"
            + "⚽ Market movement detected\n"
            + "Score: " + analysis.score().value() + "/100 " + confidence(analysis) + "\n\n"
            + "<b>" + escape(displayEventName(analysis.eventName())) + "</b>\n"
            + TelegramMessageFormat.selectionLine(displayRunner(analysis), analysis.bestBackPrice()) + "\n"
            + TelegramMessageFormat.actionLine(analysis.exchange()) + "\n\n"
            + previousOdds(analysis, previousSnapshot) + "\n"
            + "Kickoff: " + kickoff(analysis) + "\n"
            + "Market: " + escape(textOrDefault(analysis.marketName(), "n/a")) + "\n"
            + "\n"
            + "Why this signal:\n"
            + scoreReasonLines(analysis) + "\n\n"
            + "Safety:\n"
            + "DRY-RUN ONLY. No real bet placed.";
    }

    private String previousOdds(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        if (analysis.bestBackPrice() == null) {
            return "Previous odds: n/a";
        }

        Optional<BigDecimal> previousBack = previousSnapshot.map(MarketSnapshot::bestBackPrice);
        if (previousBack.isEmpty()) {
            return "Previous odds: n/a -> " + numeric(analysis.bestBackPrice());
        }

        BigDecimal percentageDelta = percentageDelta(previousBack.get(), analysis.bestBackPrice());
        if (percentageDelta == null) {
            return "Previous odds: n/a -> " + numeric(analysis.bestBackPrice());
        }

        return "Previous odds: "
            + numeric(previousBack.get())
            + " -> "
            + numeric(analysis.bestBackPrice())
            + " ("
            + signedPercent(percentageDelta)
            + ")";
    }

    private String kickoff(RunnerAnalysis analysis) {
        return analysis.marketStartTime() == null ? "n/a" : KICKOFF_FORMATTER.format(analysis.marketStartTime());
    }

    private String displayRunner(RunnerAnalysis analysis) {
        return TelegramMessageFormat.displayRunner(analysis.displayRunner());
    }

    private String displayEventName(String eventName) {
        return textOrDefault(eventName, "unknown event").replace(" v ", " vs ");
    }

    private String triggerDelta(TelegramBetAlertCandidate candidate) {
        BigDecimal value = candidate.triggerPercentageDelta();
        return value == null ? "n/a" : signedPercent(value);
    }

    private String confidence(RunnerAnalysis analysis) {
        String label = analysis.score().confidenceLabel();
        if ("High confidence".equals(label)) {
            return "🟢 " + label;
        }
        if ("Medium confidence".equals(label)) {
            return "🟡 " + label;
        }
        return label;
    }

    private String scoreReasonLines(RunnerAnalysis analysis) {
        if (analysis.score().reasons().isEmpty()
            || (analysis.score().value() == 0 && analysis.score().reasons().size() == 1)) {
            return TelegramMessageFormat.reasonLines(analysis.reason());
        }
        return analysis.score().reasons().stream()
            .map(reason -> "- " + escape(reason))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("- n/a");
    }

    private String numeric(BigDecimal value) {
        return TelegramMessageFormat.groupedNumeric(value);
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
        return TelegramMessageFormat.textOrDefault(value, fallback);
    }

    private String escape(String value) {
        return TelegramMessageFormat.escape(value);
    }
}
