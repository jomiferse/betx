package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BetxInterfaceStatusServiceTest {
    @Test
    void returnsPausedStatusWithBalanceWhenAvailable() {
        BetxInterfaceRuntimeService.RuntimeState state = new BetxInterfaceRuntimeService.RuntimeState(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            Instant.parse("2026-06-18T10:00:00Z")
        );
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(new ExchangeConfig(
            "betfair",
            true,
            new BetfairConfig(
                "user",
                "password",
                "app-key",
                null,
                new BetfairAutoBettingConfig(true, true, null, null, null)
            )
        )));
        BetxInterfaceStatusService service = new BetxInterfaceStatusService(
            () -> state,
            new StaticConfigRepository(config),
            (loadedConfig, exchange) -> Optional.of(BigDecimal.valueOf(100)),
            new BetxInterfaceProperties(Path.of("betx.yml"))
        );

        BetxInterfaceStatusView view = service.status();

        assertThat(view.status()).isEqualTo(InterfaceStatus.PAUSED);
        assertThat(view.message()).isEqualTo("BetX esta pausado.");
        assertThat(view.availableBalance()).isEqualByComparingTo("100");
        assertThat(view.manualConfirmationEnabled()).isTrue();
    }

    @Test
    void mapsBrokenConfigToNeedsAttention() {
        BetxInterfaceRuntimeService.RuntimeState state = new BetxInterfaceRuntimeService.RuntimeState(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            Instant.parse("2026-06-18T10:00:00Z")
        );
        BetxInterfaceStatusService service = new BetxInterfaceStatusService(
            () -> state,
            new FailingConfigRepository(),
            (loadedConfig, exchange) -> Optional.empty(),
            new BetxInterfaceProperties(Path.of("missing.yml"))
        );

        BetxInterfaceStatusView view = service.status();

        assertThat(view.status()).isEqualTo(InterfaceStatus.NEEDS_ATTENTION);
        assertThat(view.message()).isEqualTo("BetX necesita atencion antes de continuar.");
        assertThat(view.availableBalance()).isNull();
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private static final class FailingConfigRepository implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            throw new IllegalStateException("secret details");
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }
}
