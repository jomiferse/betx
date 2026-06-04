package com.betx.application;

import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Formats event analyzer recommendations for CLI output. */
@Component
public class EventAnalysisFormatter {
    public List<String> format(List<RunnerAnalysis> analyses) {
        Map<String, List<RunnerAnalysis>> byMarket = analyses.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                analysis -> analysis.exchange() + "|" + analysis.marketId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        return byMarket.values().stream()
            .flatMap(marketAnalyses -> formatMarket(marketAnalyses).stream())
            .toList();
    }

    private List<String> formatMarket(List<RunnerAnalysis> analyses) {
        RunnerAnalysis first = analyses.getFirst();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("EVENT ANALYSIS | " + nullSafe(first.eventName())
            + " | " + nullSafe(first.competitionName())
            + " | marketId=" + first.marketId());
        analyses.forEach(analysis -> lines.add(formatRunner(analysis)));
        return lines;
    }

    private String formatRunner(RunnerAnalysis analysis) {
        String prefix = analysis.recommendation() == RecommendationType.BET ? "BET DRY-RUN" : analysis.recommendation().name().replace('_', ' ');
        return prefix
            + " | runner=" + analysis.displayRunner()
            + " | back=" + value(analysis.bestBackPrice())
            + " | lay=" + value(analysis.bestLayPrice())
            + " | liquidity=" + value(analysis.liquidity())
            + " | reason=" + analysis.reason();
    }

    private String value(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
