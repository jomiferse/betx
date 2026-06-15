package com.betx.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Robustness diagnostics across leagues, seasons, and strategy threshold variants. */
public record BacktestRobustnessReport(
    List<String> requestedCompetitions,
    List<BacktestLeagueReport> leagueReports,
    List<BacktestWalkForwardValidation> walkForwardValidations,
    List<BacktestSensitivityReport> sensitivityReports
) {
    public BacktestRobustnessReport {
        requestedCompetitions = requestedCompetitions == null ? List.of() : List.copyOf(requestedCompetitions);
        leagueReports = leagueReports == null ? List.of() : List.copyOf(leagueReports);
        walkForwardValidations = walkForwardValidations == null ? List.of() : List.copyOf(walkForwardValidations);
        sensitivityReports = sensitivityReports == null ? List.of() : List.copyOf(sensitivityReports);
    }

    public static BacktestRobustnessReport from(
        List<String> requestedCompetitions,
        List<String> competitionsWithData,
        Map<String, BacktestResult> resultByCompetition,
        List<BigDecimal> thresholds
    ) {
        List<String> safeRequested = requestedCompetitions == null ? List.of() : List.copyOf(requestedCompetitions);
        List<String> safeWithData = competitionsWithData == null ? List.of() : List.copyOf(competitionsWithData);
        Map<String, BacktestResult> safeResults = resultByCompetition == null ? Map.of() : Map.copyOf(resultByCompetition);
        List<BigDecimal> safeThresholds = thresholds == null ? List.of() : List.copyOf(thresholds);
        List<BacktestLeagueReport> leagueReports = safeRequested.stream()
            .map(competition -> new BacktestLeagueReport(
                competition,
                safeResults.getOrDefault(competition, BacktestResult.from(0, 0, List.of())),
                safeWithData.contains(competition)
            ))
            .toList();
        List<BacktestWalkForwardValidation> walkForward = safeRequested.stream()
            .map(BacktestWalkForwardValidation::insufficientSeasons)
            .toList();
        List<BacktestSensitivityReport> sensitivity = safeRequested.stream()
            .filter(safeWithData::contains)
            .flatMap(competition -> safeThresholds.stream()
                .map(threshold -> new BacktestSensitivityReport(
                    competition,
                    threshold,
                    safeResults.getOrDefault(competition, BacktestResult.from(0, 0, List.of()))
                )))
            .toList();
        return new BacktestRobustnessReport(safeRequested, leagueReports, walkForward, sensitivity);
    }
}
