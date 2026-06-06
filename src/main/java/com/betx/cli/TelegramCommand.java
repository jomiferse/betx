package com.betx.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
    name = "telegram",
    description = "Manage Telegram alert integration.",
    subcommands = {
        TelegramConnectCommand.class,
        TelegramTestCommand.class,
        TelegramStatusCommand.class,
        TelegramBetsCommand.class
    }
)
public class TelegramCommand implements Runnable {
    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }
}
