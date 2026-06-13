package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application service for inspecting and managing bet intents. */
@Service
public class BetIntentService {
    private final BetxConfigRepository configRepository;
    private final BetIntentRepository intentRepository;
    private final Clock clock;

    @Autowired
    public BetIntentService(BetxConfigRepository configRepository, BetIntentRepository intentRepository) {
        this(configRepository, intentRepository, Clock.systemUTC());
    }

    BetIntentService(BetxConfigRepository configRepository, BetIntentRepository intentRepository, Clock clock) {
        this.configRepository = configRepository;
        this.intentRepository = intentRepository;
        this.clock = clock;
    }

    public List<BetIntent> listRecent(ConfigPath configPath, int limit) {
        var config = configRepository.load(configPath);
        return intentRepository.listRecent(config.storage().path(), limit);
    }

    public BetIntent cancel(ConfigPath configPath, String id) {
        return cancel(configPath, id, ignored -> {
        });
    }

    public BetIntent cancel(ConfigPath configPath, String id, Consumer<String> output) {
        var config = configRepository.load(configPath);
        BetIntent intent = intentRepository.findById(config.storage().path(), id)
            .orElseThrow(() -> new IllegalArgumentException("Bet intent not found: " + id));
        if (!intent.stage().isActive()) {
            throw new IllegalStateException("Bet intent is not pending: " + id);
        }
        BetIntent cancelled = intent.withStageAt(
            BetIntentStage.CANCELLED,
            intent.availableBalance(),
            intent.selectedStake(),
            "Cancelled from CLI.",
            Instant.now(clock)
        );
        intentRepository.update(config.storage().path(), cancelled);
        output.accept("BET INTENT CANCELLED | id=" + cancelled.id()
            + " | exchange=" + cancelled.exchange()
            + " | marketId=" + cancelled.marketId()
            + " | selectionId=" + cancelled.selectionId());
        return cancelled;
    }
}
