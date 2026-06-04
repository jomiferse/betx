package com.betx.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "telegram-test", hidden = true, description = "Backward-compatible alias for telegram test.")
public class TelegramTestLegacyCommand extends TelegramTestCommand {
    public TelegramTestLegacyCommand(com.betx.application.TelegramConnectionService service) {
        super(service);
    }
}
