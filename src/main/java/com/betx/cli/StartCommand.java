package com.betx.cli;

import com.betx.application.DryRunSignalFormatter;
import com.betx.application.DryRunSignalsResult;
import com.betx.application.EventAnalysisFormatter;
import com.betx.application.MatchIntelligenceAssessment;
import com.betx.application.MarketSnapshotChange;
import com.betx.application.MarketSnapshotChangeFormatter;
import com.betx.application.RunDryRunSignalsService;
import com.betx.application.StartBetxService;
import com.betx.application.TelegramBetConfirmationService;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.signal.BetSignal;
import com.betx.startup.StartupStatusRenderer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "start", description = "Start BetX market scanning.")
public class StartCommand implements Runnable {
    private static final int ONCE_CONFIRMATION_DRAIN_POLLS = 1;

    private final StartBetxService startBetxService;
    private final RunDryRunSignalsService dryRunSignalsService;
    private final StartupStatusRenderer renderer;
    private final DryRunSignalFormatter signalFormatter;
    private final MarketSnapshotChangeFormatter changeFormatter;
    private final EventAnalysisFormatter analysisFormatter;
    private final TelegramBetConfirmationService telegramBetConfirmationService;
    private final Sleeper sleeper;
    private final int callbackPollSeconds;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--once", description = "Run a single market data cycle and exit.")
    boolean once;

    @Autowired
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
        this.sleeper = Thread::sleep;
        this.callbackPollSeconds = 5;
    }

    StartCommand(
        StartBetxService startBetxService,
        RunDryRunSignalsService dryRunSignalsService,
        StartupStatusRenderer renderer,
        TelegramBetConfirmationService telegramBetConfirmationService,
        Sleeper sleeper,
        int callbackPollSeconds
    ) {
        this.startBetxService = startBetxService;
        this.dryRunSignalsService = dryRunSignalsService;
        this.renderer = renderer;
        this.telegramBetConfirmationService = telegramBetConfirmationService;
        this.signalFormatter = new DryRunSignalFormatter();
        this.changeFormatter = new MarketSnapshotChangeFormatter();
        this.analysisFormatter = new EventAnalysisFormatter();
        this.sleeper = sleeper;
        this.callbackPollSeconds = callbackPollSeconds;
    }

    @Override
    public void run() {
        ConfigPath config = new ConfigPath(configPath);
        var status = startBetxService.start(config);
        System.out.println(renderer.render(status));
        boolean requestConfirmation = status.autoBettingEnabled() && status.requestConfirmation();
        if (requestConfirmation) {
            System.out.println("BetX is running with auto-betting confirmations.");
            System.out.println("Telegram bet confirmations are enabled.");
        } else if (status.autoBettingEnabled()) {
            System.out.println("BetX auto-betting is enabled without Telegram confirmation.");
        } else {
            System.out.println("BetX auto-betting is disabled.");
            System.out.println("No real bets will be placed.");
        }

        boolean firstCycle = true;
        do {
            boolean sendTelegramAlerts = !requestConfirmation && (once || !firstCycle);
            DryRunSignalsResult result = dryRunSignalsService.run(config, sendTelegramAlerts, !requestConfirmation, System.out::println);
            boolean startupAutoBettingCycle = status.autoBettingEnabled() && !requestConfirmation && !once && firstCycle;
            if (status.autoBettingEnabled() && !startupAutoBettingCycle) {
                safeSyncBetConfirmations(config, result);
            } else if (startupAutoBettingCycle && !result.signals().isEmpty()) {
                System.out.println("AUTO BET STARTUP CYCLE SKIPPED | signals=" + result.signals().size());
            }
            printResult(result, status.autoBettingEnabled(), status.requestConfirmation());
            if (once) {
                if (requestConfirmation && !result.signals().isEmpty()) {
                    System.out.println("Waiting briefly for Telegram confirmation updates...");
                    waitForNextCycle(config, callbackPollSeconds * ONCE_CONFIRMATION_DRAIN_POLLS, true);
                }
                return;
            }
            if (result.noEnabledExchanges()) {
                return;
            }
            firstCycle = false;
            if (!waitForNextCycle(config, status.pollIntervalSeconds(), requestConfirmation)) {
                return;
            }
        } while (true);
    }

    private void printResult(DryRunSignalsResult result, boolean autoBettingEnabled, boolean requestConfirmation) {
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
            + " | signalHistory=" + result.signalHistoryEntries().size()
            + " | failures=" + result.failures().size());
        result.failures().forEach(System.out::println);
        printSnapshotChanges(result);
        if (result.runnerAnalyses().size() <= 30) {
            analysisFormatter.format(result.runnerAnalyses(), autoBettingEnabled, requestConfirmation).forEach(System.out::println);
            printIntelligence(result);
        } else {
            List<com.betx.domain.signal.RunnerAnalysis> signalAnalyses = result.runnerAnalyses().stream()
                .filter(analysis -> analysis.recommendation() == com.betx.domain.signal.RecommendationType.BET)
                .toList();
            analysisFormatter.format(signalAnalyses, autoBettingEnabled, requestConfirmation).forEach(System.out::println);
            printIntelligence(result, signalAnalyses.stream()
                .map(this::analysisKey)
                .collect(java.util.stream.Collectors.toSet()));
        }
        if (result.runnerAnalyses().isEmpty()) {
            System.out.println("No event analyses found.");
            return;
        }

        result.signals().forEach(signal -> System.out.println(signalFormatter.format(signal)));
    }

    private void safeSyncBetConfirmations(ConfigPath config, DryRunSignalsResult result) {
        try {
            telegramBetConfirmationService.sync(config, result, System.out::println);
        } catch (RuntimeException exc) {
            System.out.println("TELEGRAM BET SYNC WARNING | message=" + nullSafe(exc.getMessage()));
        }
    }

    private void printIntelligence(DryRunSignalsResult result) {
        Set<String> signalKeys = result.signals().stream()
            .map(this::signalKey)
            .collect(java.util.stream.Collectors.toSet());
        printIntelligence(result, signalKeys);
    }

    private void printIntelligence(DryRunSignalsResult result, Set<String> visibleKeys) {
        Map<String, String> runnersByKey = result.runnerAnalyses().stream()
            .collect(java.util.stream.Collectors.toMap(
                this::analysisKey,
                com.betx.domain.signal.RunnerAnalysis::displayRunner,
                (left, right) -> left
            ));
        result.intelligenceAssessments().stream()
            .filter(assessment -> visibleKeys.contains(intelligenceKey(assessment)))
            .forEach(assessment -> System.out.println(
                "INTELLIGENCE | runner=" + runnersByKey.getOrDefault(intelligenceKey(assessment), "unknown")
                    + " | decision=" + assessment.decision()
                    + " | confidence=" + assessment.confidence() + "/100"
                    + " | summary=" + assessment.summary()
            ));
    }

    private void printSnapshotChanges(DryRunSignalsResult result) {
        List<MarketSnapshotChange> relevantChanges = result.changes().stream()
            .filter(changeFormatter::isRelevant)
            .toList();
        List<MarketSnapshotChange> changesToPrint = relevantChanges;
        if (result.runnerAnalyses().size() > 30) {
            Set<String> signalKeys = result.signals().stream()
                .map(this::signalKey)
                .collect(java.util.stream.Collectors.toSet());
            changesToPrint = relevantChanges.stream()
                .filter(change -> signalKeys.contains(changeKey(change)))
                .toList();
        }
        changesToPrint.forEach(change -> System.out.println(changeFormatter.format(change)));
        int hidden = relevantChanges.size() - changesToPrint.size();
        if (hidden > 0) {
            System.out.println("Snapshot changes summarized | relevant=" + relevantChanges.size()
                + " | shown=" + changesToPrint.size()
                + " | hidden=" + hidden);
        }
    }

    private String signalKey(BetSignal signal) {
        return signal.exchange() + "|" + signal.marketId() + "|" + signal.selectionId();
    }

    private String analysisKey(com.betx.domain.signal.RunnerAnalysis analysis) {
        return analysis.exchange() + "|" + analysis.marketId() + "|" + analysis.selectionId();
    }

    private String intelligenceKey(MatchIntelligenceAssessment assessment) {
        return assessment.exchange() + "|" + assessment.marketId() + "|" + assessment.selectionId();
    }

    private String changeKey(MarketSnapshotChange change) {
        return change.current().exchange() + "|" + change.current().marketId() + "|" + change.current().selectionId();
    }

    private boolean waitForNextCycle(ConfigPath config, int seconds, boolean liveMode) {
        long remainingMillis = Math.max(seconds, 0) * 1_000L;
        long pollMillis = Math.max(callbackPollSeconds, 1) * 1_000L;
        while (remainingMillis > 0L) {
            long pauseMillis = Math.min(remainingMillis, pollMillis);
            if (!sleep(pauseMillis)) {
                return false;
            }
            remainingMillis -= pauseMillis;
            if (liveMode) {
                safeSyncBetConfirmations(config, new DryRunSignalsResult(List.of(), List.of(), false));
            }
        }
        return true;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "Unknown error." : value;
    }

    private boolean sleep(long millis) {
        try {
            sleeper.sleep(millis);
            return true;
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
