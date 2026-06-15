package com.betx.cli;

import com.betx.adapter.backtest.FootballDataBacktestCsvConverter;
import com.betx.adapter.backtest.FootballDataConversionResult;
import com.betx.application.BacktestValidationException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "convert-football-data", description = "Convert Football-Data CSV files into BetX backtest history CSV.")
public class FootballDataBacktestConvertCommand implements Runnable {
    private final FootballDataBacktestCsvConverter converter;

    @Option(names = {"--input", "-i"}, required = true, description = "Path to Football-Data CSV. Repeat to combine files.")
    List<Path> inputPaths;

    @Option(names = {"--output", "-o"}, required = true, description = "Path to write BetX normalized history CSV.")
    Path outputPath;

    @Option(names = {"--season"}, description = "Explicit season label, for example 2025/26. Inferred from match date when omitted.")
    String season;

    @Option(names = {"--odds-source"}, defaultValue = "closing-average", description = "Odds source: opening-bookmaker, closing-average, or opening-closing.")
    String oddsSource;

    @Autowired
    public FootballDataBacktestConvertCommand(FootballDataBacktestCsvConverter converter) {
        this.converter = converter;
    }

    @Override
    public void run() {
        String selectedOddsSource = oddsSource();
        FootballDataConversionResult result = converter.convert(inputPaths, outputPath, season, selectedOddsSource);
        System.out.println("Football-Data conversion complete | matches=" + result.matchesRead()
            + " | rows=" + result.rowsWritten()
            + " | duplicatesSkipped=" + result.duplicatesSkipped()
            + " | oddsSource=" + selectedOddsSource
            + " | output=" + outputPath);
    }

    private String oddsSource() {
        try {
            String selectedOddsSource = oddsSource == null || oddsSource.isBlank() ? "closing-average" : oddsSource.strip();
            if ("opening-closing".equalsIgnoreCase(selectedOddsSource)) {
                return "opening-closing";
            }
            return com.betx.adapter.backtest.FootballDataOddsSource.fromId(selectedOddsSource).id();
        } catch (IllegalArgumentException exc) {
            throw new BacktestValidationException("--odds-source must be opening-bookmaker, closing-average, or opening-closing.", exc);
        }
    }
}
