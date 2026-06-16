package com.betx.cli;

import com.betx.application.BetIntentService;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntent;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
    name = "bets",
    description = "List recent bet intents.",
    subcommands = {TelegramBetsCancelCommand.class}
)
public class TelegramBetsCommand implements Runnable {
    private final BetIntentService service;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--limit", defaultValue = "20", description = "Maximum intents to list.")
    int limit;

    public TelegramBetsCommand(BetIntentService service) {
        this.service = service;
    }

    @Override
    public void run() {
        var intents = service.listRecent(new ConfigPath(configPath), limit);
        if (intents.isEmpty()) {
            System.out.println("No bet intents found.");
            return;
        }
        intents.forEach(intent -> System.out.println(format(intent)));
    }

    private String format(BetIntent intent) {
        return "id=" + intent.id()
            + " | source=" + intent.source()
            + " | stage=" + intent.stage()
            + " | event=" + value(intent.eventName())
            + " | runner=" + value(intent.displayRunner())
            + " | side=" + intent.side()
            + " | marketId=" + value(intent.marketId())
            + " | selectionId=" + intent.selectionId()
            + " | stake=" + money(intent.selectedStake())
            + " | settlement=" + value(intent.settlementResult() == null ? null : intent.settlementResult().name())
            + " | pnl=" + money(intent.realizedProfitLoss())
            + " | updated=" + intent.updatedAt();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "n/a" : value.strip();
    }

    private String money(BigDecimal value) {
        return value == null ? "n/a" : value.toPlainString();
    }
}
