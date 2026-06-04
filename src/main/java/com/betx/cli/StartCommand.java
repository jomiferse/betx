package com.betx.cli;

import com.betx.application.DryRunSignalFormatter;
import com.betx.application.DryRunSignalsResult;
import com.betx.application.EventAnalysisFormatter;
import com.betx.application.MarketSnapshotChangeFormatter;
import com.betx.application.RunDryRunSignalsService;
import com.betx.application.StartBetxService;
import com.betx.application.TelegramBetConfirmationService;
import com.betx.domain.config.ConfigPath;
import com.betx.startup.StartupStatusRenderer;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "start", description = "Start BetX in dry-run mode.")
public class StartCommand implements Runnable {
    private final StartBetxService startBetxService;
    private final RunDryRunSignalsService dryRunSignalsService;
    private final StartupStatusRenderer renderer;
    private final DryRunSignalFormatter signalFormatter;
    private final MarketSnapshotChangeFormatter changeFormatter;
    private final EventAnalysisFormatter analysisFormatter;
    private final TelegramBetConfirmationService telegramBetConfirmationService;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--once", description = "Run a single market data cycle and exit.")
    boolean once;

    public StartCommand(
        StartBetxService startBetxService,
        RunDryRunSignalsService dryRunSignalsService,
        StartupStatusRenderer renderer,
        TelegramBetConfirmationService telegramBetConfirmationService
    ) {
        this.startBetxService = startBetxService;
        this.dryRunSignalsService = dryRunSignalsService;
        this.renderer = renderer;
        this.telegramBetConfirmationService = telegramBetConfirmationService;
        this.signalFormatter = new DryRunSignalFormatter();
        this.changeFormatter = new MarketSnapshotChangeFormatter();
        this.analysisFormatter = new EventAnalysisFormatter();
    }

    @Override
    public void run() {
        ConfigPath config = new ConfigPath(configPath);
        var status = startBetxService.start(config);
        System.out.println(renderer.render(status));
        if (status.liveBettingEnabled()) {
            System.out.println("BetX is running in live mode.");
            System.out.println("Telegram bet confirmations are enabled.");
        } else {
            System.out.println("BetX is running in dry-run mode.");
            System.out.println("No real bets will be placed.");
        }

        boolean firstCycle = true;
        do {
            boolean sendTelegramAlerts = !status.liveBettingEnabled() && (once || !firstCycle);
            DryRunSignalsResult result = dryRunSignalsService.run(config, sendTelegramAlerts);
            if (status.liveBettingEnabled()) {
                telegramBetConfirmationService.sync(config, result);
            }
            printResult(result);
            if (result.noEnabledExchanges() || once) {
                return;
            }
            firstCycle = false;
            sleep(status.pollIntervalSeconds());
        } while (true);
    }

    private void printResult(DryRunSignalsResult result) {
        if (result.noEnabledExchanges()) {
            System.out.println("No enabled exchanges configured.");
            return;
        }

        System.out.println("Cycle complete | snapshots=" + result.snapshotsSaved()
            + " | comparisons=" + result.comparisonsCalculated()
            + " | events=" + result.eventsRead()
            + " | ignoredEvents=" + result.ignoredEvents()
            + " | markets=" + result.marketsRead()
            + " | ignoredMarkets=" + result.ignoredMarkets()
            + " | runnersAnalyzed=" + result.runnerAnalyses().size()
            + " | signals=" + result.signals().size()
            + " | failures=" + result.failures().size());
        result.failures().forEach(System.out::println);
        result.changes().stream()
            .filter(changeFormatter::isRelevant)
            .forEach(change -> System.out.println(changeFormatter.format(change)));
        if (result.runnerAnalyses().size() <= 30 || once) {
            analysisFormatter.format(result.runnerAnalyses()).forEach(System.out::println);
        } else {
            analysisFormatter.format(result.runnerAnalyses().stream()
                .filter(analysis -> analysis.recommendation() == com.betx.domain.signal.RecommendationType.BET)
                .toList()).forEach(System.out::println);
        }
        if (result.runnerAnalyses().isEmpty()) {
            System.out.println("No event analyses found.");
            return;
        }

        result.signals().forEach(signal -> System.out.println(signalFormatter.format(signal)));
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
    }
}
