package com.betx.application;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import com.betx.common.BetxException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceActivityService {
    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final BetIntentRepository intentRepository;
    private final BetxConfigRepository configRepository;
    private final BetxInterfaceProperties properties;

    public BetxInterfaceActivityService(
        BetIntentRepository intentRepository,
        BetxConfigRepository configRepository,
        BetxInterfaceProperties properties
    ) {
        this.intentRepository = intentRepository;
        this.configRepository = configRepository;
        this.properties = properties;
    }

    public List<BetxInterfaceActivityItem> recent() {
        try {
            BetxConfig config = configRepository.load(new ConfigPath(properties.configPath()));
            return intentRepository.listRecent(config.storage().path(), RECENT_ACTIVITY_LIMIT).stream()
                .map(this::item)
                .toList();
        } catch (BetxException | IllegalStateException exc) {
            return List.of();
        }
    }

    private BetxInterfaceActivityItem item(BetIntent intent) {
        return new BetxInterfaceActivityItem(
            intent.id(),
            blankToDash(intent.eventName()),
            blankToDash(intent.runnerName()),
            intent.odds(),
            intent.selectedStake(),
            status(intent.stage()),
            intent.settlementResult() == null ? null : intent.settlementResult().name(),
            intent.realizedProfitLoss(),
            intent.updatedAt()
        );
    }

    private String status(BetIntentStage stage) {
        return switch (stage) {
            case AWAITING_CONFIRMATION, AWAITING_STAKE -> "PENDING";
            case EXECUTED -> "EXECUTED";
            case SETTLED -> "SETTLED";
            case CANCELLED -> "CANCELLED";
            case FAILED -> "REJECTED";
        };
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
