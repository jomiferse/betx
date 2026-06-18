package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StorageConfig;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BetxInterfaceActivityServiceTest {
    @Test
    void mapsRecentOperationsToUserFacingRows() {
        RecordingBetIntentRepository repository = new RecordingBetIntentRepository(List.of(new BetIntent(
            "intent-1",
            "betfair",
            "market-1",
            42L,
            "Real Madrid vs Barcelona",
            "Match Odds",
            "Empate",
            "Movimiento favorable",
            BigDecimal.valueOf(3.2),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(5),
            "accepted",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-18T09:00:00Z"),
            Instant.parse("2026-06-18T09:01:00Z")
        ).withSettlement(
            BetIntentStage.SETTLED,
            BetSettlementResult.WIN,
            BigDecimal.valueOf(4),
            Instant.parse("2026-06-18T09:20:00Z"),
            "settled"
        )));
        BetxInterfaceActivityService service = new BetxInterfaceActivityService(
            repository,
            new StaticConfigRepository(),
            new BetxInterfaceProperties(Path.of("betx.yml"))
        );

        List<BetxInterfaceActivityItem> items = service.recent();

        assertThat(repository.databasePath).isEqualTo("data.db");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("intent-1");
            assertThat(item.event()).isEqualTo("Real Madrid vs Barcelona");
            assertThat(item.selection()).isEqualTo("Empate");
            assertThat(item.odds()).isEqualByComparingTo("3.2");
            assertThat(item.amount()).isEqualByComparingTo("5");
            assertThat(item.status()).isEqualTo("SETTLED");
            assertThat(item.result()).isEqualTo("WIN");
            assertThat(item.netPnl()).isEqualByComparingTo("4");
            assertThat(item.updatedAt()).isEqualTo(Instant.parse("2026-06-18T09:20:00Z"));
        });
    }

    private static final class StaticConfigRepository implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            BetxConfig defaults = BetxConfig.defaults();
            return new BetxConfig(
                defaults.app(),
                defaults.telegram(),
                defaults.betfair(),
                defaults.exchanges(),
                defaults.marketData(),
                new StorageConfig(defaults.storage().type(), "data.db"),
                defaults.risk(),
                defaults.strategies(),
                defaults.ml()
            );
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private static final class RecordingBetIntentRepository implements BetIntentRepository {
        private final List<BetIntent> intents;
        private String databasePath;

        private RecordingBetIntentRepository(List<BetIntent> intents) {
            this.intents = intents;
        }

        @Override
        public Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public Optional<BetIntent> findLatestByKeySince(
            String databasePath,
            String exchange,
            String marketId,
            long selectionId,
            Instant since
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<BetIntent> findById(String databasePath, String id) {
            return Optional.empty();
        }

        @Override
        public List<BetIntent> listRecent(String databasePath, int limit) {
            this.databasePath = databasePath;
            return intents.stream().limit(limit).toList();
        }

        @Override
        public List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit) {
            return List.of();
        }

        @Override
        public long countByStages(String databasePath, List<BetIntentStage> stages) {
            return 0L;
        }

        @Override
        public BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since) {
            return BigDecimal.ZERO;
        }

        @Override
        public void save(String databasePath, BetIntent intent) {
        }

        @Override
        public void update(String databasePath, BetIntent intent) {
        }
    }
}
