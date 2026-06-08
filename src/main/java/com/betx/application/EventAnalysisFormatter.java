package com.betx.application;

import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Formats event analyzer recommendations for CLI output. */
@Component
public class EventAnalysisFormatter {
    public List<String> format(List<RunnerAnalysis> analyses) {
        return format(analyses, false, false);
    }

    public List<String> format(List<RunnerAnalysis> analyses, boolean autoBettingEnabled) {
        return format(analyses, autoBettingEnabled, false);
    }

    public List<String> format(List<RunnerAnalysis> analyses, boolean autoBettingEnabled, boolean requestConfirmation) {
        Map<String, List<RunnerAnalysis>> byMarket = analyses.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                analysis -> analysis.exchange() + "|" + analysis.marketId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        return byMarket.values().stream()
            .flatMap(marketAnalyses -> formatMarket(marketAnalyses, autoBettingEnabled, requestConfirmation).stream())
            .toList();
    }

    private List<String> formatMarket(List<RunnerAnalysis> analyses, boolean autoBettingEnabled, boolean requestConfirmation) {
        RunnerAnalysis first = analyses.getFirst();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("EVENT ANALYSIS | " + nullSafe(first.eventName())
            + " | " + nullSafe(first.competitionName())
            + " | marketId=" + first.marketId());
        analyses.forEach(analysis -> lines.add(formatRunner(analysis, autoBettingEnabled, requestConfirmation)));
        return lines;
    }

    private String formatRunner(RunnerAnalysis analysis, boolean autoBettingEnabled, boolean requestConfirmation) {
        String prefix = analysis.recommendation() == RecommendationType.BET
            ? betPrefix(autoBettingEnabled, requestConfirmation)
            : analysis.recommendation().name().replace('_', ' ');
        return prefix
            + " | runner=" + analysis.displayRunner()
            + " | back=" + value(analysis.bestBackPrice())
            + " | lay=" + value(analysis.bestLayPrice())
            + " | liquidity=" + value(analysis.liquidity())
            + " | score=" + analysis.score().value() + "/100"
            + " | reason=" + reason(analysis.reason(), autoBettingEnabled);
    }

    private String betPrefix(boolean autoBettingEnabled, boolean requestConfirmation) {
        if (!autoBettingEnabled) {
            return "BET SIGNAL";
        }
        return requestConfirmation ? "BET CONFIRMATION" : "BET AUTO";
    }

    private String reason(String reason, boolean autoBettingEnabled) {
        String sanitized = Arrays.stream(reason.split(","))
            .map(String::strip)
            .filter(token -> !"dry_run_only".equals(token.toLowerCase(Locale.ROOT)))
            .filter(token -> !token.isBlank())
            .reduce((left, right) -> left + ", " + right)
            .orElse("unspecified");
        return sanitized;
    }

    private String value(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
