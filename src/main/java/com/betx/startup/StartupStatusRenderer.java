package com.betx.startup;

import com.betx.domain.startup.StartupStatus;
import org.springframework.stereotype.Component;

@Component
public class StartupStatusRenderer {
    public String render(StartupStatus status) {
        return String.join(
            "\n",
            "BetX startup status",
            "mode: " + status.mode(),
            "telegram: " + enabled(status.telegramEnabled()),
            "ml: " + enabled(status.mlEnabled()),
            "live betting: " + enabled(status.liveBettingEnabled()),
            "storage path: " + status.storagePath(),
            "poll interval: " + status.pollIntervalSeconds() + "s"
        );
    }

    private String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }
}
