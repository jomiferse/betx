package com.betx.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
    name = "betx",
    mixinStandardHelpOptions = true,
    description = "BetX terminal-first betting signals engine.",
    subcommands = {
        InitCommand.class,
        StartCommand.class,
        TelegramCommand.class,
        BetfairCommand.class,
        TelegramTestLegacyCommand.class
    }
)
public class BetxRootCommand implements Runnable {
    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }
}
