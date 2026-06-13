package com.betx.cli;

import com.betx.application.BacktestResultFormatter;
import com.betx.application.RunBacktestService;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
    name = "backtest",
    description = "Replay normalized historical market data and print simulated performance.",
    subcommands = {
        FootballDataBacktestConvertCommand.class
    }
)
public class BacktestCommand implements Runnable {
    private final RunBacktestService backtestService;
    private final BacktestResultFormatter formatter;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = {"--input", "-i"}, description = "Path to normalized historical backtest CSV.")
    Path inputPath;

    @Autowired
    public BacktestCommand(RunBacktestService backtestService) {
        this(backtestService, new BacktestResultFormatter());
    }

    BacktestCommand(RunBacktestService backtestService, BacktestResultFormatter formatter) {
        this.backtestService = backtestService;
        this.formatter = formatter;
    }

    @Override
    public void run() {
        if (inputPath == null) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "Missing required option: '--input=<inputPath>'"
            );
        }
        formatter.format(backtestService.run(new ConfigPath(configPath), inputPath))
            .forEach(System.out::println);
    }
}
