package com.betx.cli;

import com.betx.adapter.backtest.FootballDataBacktestCsvConverter;
import com.betx.adapter.backtest.FootballDataConversionResult;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "convert-football-data", description = "Convert Football-Data CSV files into BetX backtest history CSV.")
public class FootballDataBacktestConvertCommand implements Runnable {
    private final FootballDataBacktestCsvConverter converter;

    @Option(names = {"--input", "-i"}, required = true, description = "Path to Football-Data CSV, for example SP1.csv.")
    Path inputPath;

    @Option(names = {"--output", "-o"}, required = true, description = "Path to write BetX normalized history CSV.")
    Path outputPath;

    @Autowired
    public FootballDataBacktestConvertCommand(FootballDataBacktestCsvConverter converter) {
        this.converter = converter;
    }

    @Override
    public void run() {
        FootballDataConversionResult result = converter.convert(inputPath, outputPath);
        System.out.println("Football-Data conversion complete | matches=" + result.matchesRead()
            + " | rows=" + result.rowsWritten()
            + " | output=" + outputPath);
    }
}
