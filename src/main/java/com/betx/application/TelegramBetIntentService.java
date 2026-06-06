package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.TelegramBetIntentRepository;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.telegram.TelegramBetIntent;
import com.betx.domain.telegram.TelegramBetIntentStage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application service for inspecting and managing Telegram bet intents. */
@Service
public class TelegramBetIntentService {
    private final BetxConfigRepository configRepository;
    private final TelegramBetIntentRepository intentRepository;
    private final Clock clock;

    @Autowired
    public TelegramBetIntentService(BetxConfigRepository configRepository, TelegramBetIntentRepository intentRepository) {
        this(configRepository, intentRepository, Clock.systemUTC());
    }

    TelegramBetIntentService(BetxConfigRepository configRepository, TelegramBetIntentRepository intentRepository, Clock clock) {
        this.configRepository = configRepository;
        this.intentRepository = intentRepository;
        this.clock = clock;
    }

    public List<TelegramBetIntent> listRecent(ConfigPath configPath, int limit) {
        var config = configRepository.load(configPath);
        return intentRepository.listRecent(config.storage().path(), limit);
    }

    public TelegramBetIntent cancel(ConfigPath configPath, String id) {
        var config = configRepository.load(configPath);
        TelegramBetIntent intent = intentRepository.findById(config.storage().path(), id)
            .orElseThrow(() -> new IllegalArgumentException("Telegram bet intent not found: " + id));
        if (!intent.stage().isActive()) {
            throw new IllegalStateException("Telegram bet intent is not pending: " + id);
        }
        TelegramBetIntent cancelled = intent.withStageAt(
            TelegramBetIntentStage.CANCELLED,
            intent.availableBalance(),
            intent.selectedStake(),
            "Cancelled from CLI.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), cancelled);
        System.out.println("TELEGRAM BET INTENT CANCELLED | id=" + cancelled.id()
            + " | exchange=" + cancelled.exchange()
            + " | marketId=" + cancelled.marketId()
            + " | selectionId=" + cancelled.selectionId());
        return cancelled;
    }
}
