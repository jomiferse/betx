package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TelegramMessageFormat {
    private static final Map<String, String> REASON_LABELS = new LinkedHashMap<>();

    static {
        REASON_LABELS.put("liquidity_ok", "Liquidity OK");
        REASON_LABELS.put("spread_ok", "Spread OK");
        REASON_LABELS.put("odds_range_ok", "Odds range OK");
        REASON_LABELS.put("favorable_odds_movement", "Odds moved favourably");
        REASON_LABELS.put("favorable_liquidity_movement", "Liquidity improved");
    }

    private TelegramMessageFormat() {
    }

    static String selectionLine(String runner, BigDecimal odds) {
        String displayRunner = displayRunner(runner);
        String outcome = "Draw".equalsIgnoreCase(displayRunner) ? "Draw" : displayRunner + " to win";
        return "Bet: " + escape(outcome) + " @ " + numeric(odds);
    }

    static String actionLine(String exchange) {
        return "Action: BACK on " + escape(textOrDefault(exchange, "n/a"));
    }

    static String reasonLines(String reason) {
        return reasonLines(reason, Optional.empty());
    }

    static String reasonLines(String reason, Optional<String> excludedToken) {
        Set<String> labels = new LinkedHashSet<>();
        String excluded = excludedToken.map(TelegramMessageFormat::normalizeToken).orElse(null);
        Arrays.stream(textOrDefault(reason, "").split(","))
            .map(TelegramMessageFormat::normalizeToken)
            .filter(token -> !token.isBlank())
            .filter(token -> !"dry_run_only".equals(token))
            .filter(token -> excluded == null || !excluded.equals(token))
            .map(TelegramMessageFormat::reasonLabel)
            .forEach(labels::add);

        if (labels.isEmpty()) {
            return "- n/a";
        }
        return labels.stream()
            .map(label -> "- " + escape(label))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("- n/a");
    }

    static String triggerLabel(TelegramBetAlertTrigger trigger) {
        return switch (trigger) {
            case ODDS_MOVEMENT -> "Odds moved favourably";
            case LIQUIDITY_MOVEMENT -> "Liquidity improved";
        };
    }

    static String displayRunner(String runner) {
        String value = textOrDefault(runner, "n/a");
        return "The Draw".equalsIgnoreCase(value) ? "Draw" : value;
    }

    static String numeric(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String groupedNumeric(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return String.format(Locale.US, "%,.2f", value);
    }

    static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    static String escape(String value) {
        return textOrDefault(value, "n/a")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String normalizeToken(String value) {
        return textOrDefault(value, "")
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .strip();
    }

    private static String reasonLabel(String token) {
        String label = REASON_LABELS.get(token);
        if (label != null) {
            return label;
        }
        String readable = token.replace('_', ' ');
        if (readable.isBlank()) {
            return "n/a";
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }
}
