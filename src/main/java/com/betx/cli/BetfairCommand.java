package com.betx.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
    name = "betfair",
    description = "Manage Betfair API access and read-only market data.",
    subcommands = {
        BetfairTestCommand.class,
        BetfairMarketsCommand.class
    }
)
public class BetfairCommand implements Runnable {
    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }
}
