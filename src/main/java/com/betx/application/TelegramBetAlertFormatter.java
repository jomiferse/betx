package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Formats BET recommendations for Telegram HTML alerts. */
public class TelegramBetAlertFormatter {
    private static final ZoneId ALERT_ZONE = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter KICKOFF_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z", Locale.ENGLISH)
        .withZone(ALERT_ZONE);

    public String format(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return TelegramBetAlertCandidate.tryFrom(analysis, previousSnapshot)
            .map(candidate -> format(candidate))
            .orElseGet(() -> legacyFormat(analysis, previousSnapshot));
    }

    String formatLiveConfirmation(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return formatLiveConfirmation(analysis, previousSnapshot, Optional.empty());
    }

    String formatLiveConfirmation(
        RunnerAnalysis analysis,
        Optional<MarketSnapshot> previousSnapshot,
        Optional<MatchIntelligenceAssessment> intelligenceAssessment
    ) {
        return TelegramBetAlertCandidate.tryFrom(analysis, previousSnapshot)
            .map(candidate -> formatLiveConfirmation(candidate, intelligenceAssessment))
            .orElseGet(() -> legacyLiveConfirmationFormat(analysis, previousSnapshot, intelligenceAssessment));
    }

    String format(TelegramBetAlertCandidate candidate) {
        RunnerAnalysis analysis = candidate.analysis();
        return "<b>BETX SIGNAL</b>\n"
            + "SIGNAL ONLY\n\n"
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
            + "SIGNAL ONLY. No real bet placed.";
    }

    private String formatLiveConfirmation(TelegramBetAlertCandidate candidate) {
        return formatLiveConfirmation(candidate, Optional.empty());
    }

    private String formatLiveConfirmation(
        TelegramBetAlertCandidate candidate,
        Optional<MatchIntelligenceAssessment> intelligenceAssessment
    ) {
        if (requiresReview(intelligenceAssessment)) {
            return formatReviewRequired(candidate, intelligenceAssessment.get());
        }
        RunnerAnalysis analysis = candidate.analysis();
        return "<b>BETX SIGNAL</b>\n"
            + "BET CONFIRMATION\n\n"
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
            + intelligenceSection(intelligenceAssessment)
            + "Safety:\n"
            + "No bet is placed until you confirm and choose stake.\n"
            + "Betfair auto-betting is enabled. Confirmation required.\n\n"
            + "Confirm bet?";
    }

    private String legacyFormat(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return "<b>BETX SIGNAL</b>\n"
            + "SIGNAL ONLY\n\n"
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
            + "SIGNAL ONLY. No real bet placed.";
    }

    private String legacyLiveConfirmationFormat(RunnerAnalysis analysis, Optional<MarketSnapshot> previousSnapshot) {
        return legacyLiveConfirmationFormat(analysis, previousSnapshot, Optional.empty());
    }

    private String legacyLiveConfirmationFormat(
        RunnerAnalysis analysis,
        Optional<MarketSnapshot> previousSnapshot,
        Optional<MatchIntelligenceAssessment> intelligenceAssessment
    ) {
        if (requiresReview(intelligenceAssessment)) {
            return legacyReviewRequiredFormat(analysis, previousSnapshot, intelligenceAssessment.get());
        }
        return "<b>BETX SIGNAL</b>\n"
            + "BET CONFIRMATION\n\n"
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
            + intelligenceSection(intelligenceAssessment)
            + "Safety:\n"
            + "No bet is placed until you confirm and choose stake.\n"
            + "Betfair auto-betting is enabled. Confirmation required.\n\n"
            + "Confirm bet?";
    }

    private String formatReviewRequired(TelegramBetAlertCandidate candidate, MatchIntelligenceAssessment assessment) {
        RunnerAnalysis analysis = candidate.analysis();
        return reviewRequiredHeader(analysis, candidate.previousSnapshot(), assessment)
            + "Trigger:\n"
            + "- " + escape(TelegramMessageFormat.triggerLabel(candidate.trigger()) + ": " + triggerDelta(candidate)) + "\n"
            + triggerReasonLines(analysis)
            + "\n"
            + reviewContext(assessment)
            + reviewRisk(analysis, assessment);
    }

    private String legacyReviewRequiredFormat(
        RunnerAnalysis analysis,
        Optional<MarketSnapshot> previousSnapshot,
        MatchIntelligenceAssessment assessment
    ) {
        return reviewRequiredHeader(analysis, previousSnapshot, assessment)
            + "Trigger:\n"
            + triggerReasonLines(analysis)
            + "\n"
            + reviewContext(assessment)
            + reviewRisk(analysis, assessment);
    }

    private String reviewRequiredHeader(RunnerAnalysis analysis, MatchIntelligenceAssessment assessment) {
        return reviewRequiredHeader(analysis, Optional.empty(), assessment);
    }

    private String reviewRequiredHeader(
        RunnerAnalysis analysis,
        Optional<MarketSnapshot> previousSnapshot,
        MatchIntelligenceAssessment assessment
    ) {
        return "<b>BETX SIGNAL</b>\n"
            + "BET REVIEW REQUIRED\n\n"
            + "⚽ Market movement detected\n"
            + "Market Signal: " + analysis.score().value() + "/100 " + marketConfidenceIcon(analysis) + "\n"
            + "Context Score: " + assessment.confidence() + "/100 " + contextConfidenceIcon(assessment) + "\n"
            + "Final Recommendation: " + assessment.decision() + " — no automatic bet\n\n"
            + "<b>" + escape(displayEventName(analysis.eventName())) + "</b>\n"
            + "Market: " + escape(textOrDefault(analysis.marketName(), "n/a")) + "\n"
            + "Selection: " + escape(displayRunner(analysis)) + "\n"
            + TelegramMessageFormat.actionLine(analysis.exchange()) + "\n"
            + "Current odds: " + numeric(analysis.bestBackPrice()) + "\n"
            + previousOdds(analysis, previousSnapshot) + "\n\n"
            + "Break-even probability: " + breakEvenProbability(analysis.bestBackPrice()) + "\n"
            + "Estimated probability: not available\n"
            + "Edge: uncertain / narrow\n\n";
    }

    private String triggerReasonLines(RunnerAnalysis analysis) {
        return scoreReasonLines(analysis) + "\n";
    }

    private String reviewContext(MatchIntelligenceAssessment assessment) {
        String reasons = assessment.reasons().isEmpty()
            ? "- " + escape(assessment.summary()) + "\n"
            : assessment.reasons().stream()
                .limit(4)
                .map(reason -> "- " + escape(reason))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("") + "\n";
        return "Context:\n" + reasons + "\n";
    }

    private String reviewRisk(RunnerAnalysis analysis, MatchIntelligenceAssessment assessment) {
        String externalRisks = assessment.risks().stream()
            .limit(2)
            .map(risk -> "- " + escape(risk))
            .reduce((left, right) -> left + "\n" + right)
            .map(value -> value + "\n")
            .orElse("");
        return "Risk:\n"
            + externalRisks
            + "⚠️ Edge is not strong enough for auto-confirmation.\n"
            + "Recommended action: " + assessment.decision() + " until stronger price or confirmation signal.\n\n"
            + "Suggested rule:\n"
            + "Only consider BACK " + escape(displayRunner(analysis)) + " if odds >= " + suggestedMinimumOdds(analysis)
            + " or model probability >= 29%.\n\n"
            + "No bet will be placed until you confirm stake.";
    }

    private boolean requiresReview(Optional<MatchIntelligenceAssessment> assessment) {
        return assessment
            .map(value -> value.decision() == MatchIntelligenceDecision.WATCH || value.decision() == MatchIntelligenceDecision.REJECT)
            .orElse(false);
    }

    private String intelligenceSection(Optional<MatchIntelligenceAssessment> intelligenceAssessment) {
        return intelligenceAssessment
            .filter(assessment -> assessment.decision() != MatchIntelligenceDecision.UNAVAILABLE)
            .map(assessment -> "OpenRouter recommendation:\n"
                + assessment.decision().name() + " (" + assessment.confidence() + "/100)\n"
                + escape(assessment.summary()) + "\n"
                + intelligenceReasons(assessment)
                + intelligenceSource(assessment)
                + "\n")
            .orElse("");
    }

    private String intelligenceReasons(MatchIntelligenceAssessment assessment) {
        if (assessment.reasons().isEmpty()) {
            return "";
        }
        return assessment.reasons().stream()
            .limit(3)
            .map(reason -> "- " + escape(reason))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("") + "\n";
    }

    private String intelligenceSource(MatchIntelligenceAssessment assessment) {
        return assessment.sources().stream()
            .findFirst()
            .map(source -> "Source: " + escape(source.url() == null ? source.title() : source.url()) + "\n")
            .orElse("");
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

    private String marketConfidenceIcon(RunnerAnalysis analysis) {
        return analysis.score().value() >= 70 ? "🟢" : analysis.score().value() >= 50 ? "🟡" : "";
    }

    private String contextConfidenceIcon(MatchIntelligenceAssessment assessment) {
        return assessment.confidence() >= 70 ? "🟢" : assessment.confidence() >= 50 ? "🟡" : "";
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

    private String breakEvenProbability(BigDecimal odds) {
        if (odds == null || odds.compareTo(BigDecimal.ZERO) <= 0) {
            return "n/a";
        }
        return BigDecimal.ONE
            .divide(odds, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    private String suggestedMinimumOdds(RunnerAnalysis analysis) {
        if ("draw".equalsIgnoreCase(displayRunner(analysis))) {
            return "3.85";
        }
        if (analysis.bestBackPrice() == null) {
            return "n/a";
        }
        return analysis.bestBackPrice()
            .add(BigDecimal.valueOf(0.10))
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
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
