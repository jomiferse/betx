package com.betx.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetxInterfaceActivityItem;
import com.betx.application.BetxInterfaceActivityService;
import com.betx.application.BetxInterfaceProperties;
import com.betx.application.BetxInterfaceRuntimeService;
import com.betx.application.BetxInterfaceStatusService;
import com.betx.application.BetxInterfaceStatusView;
import com.betx.application.InterfaceStatus;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BetxInterfaceControllerTest {
    @Test
    void exposesStatusAndActivity() {
        BetxInterfaceStatusService statusService = Mockito.mock(BetxInterfaceStatusService.class);
        BetxInterfaceRuntimeService runtimeService = Mockito.mock(BetxInterfaceRuntimeService.class);
        BetxInterfaceActivityService activityService = Mockito.mock(BetxInterfaceActivityService.class);
        BetxInterfaceProperties properties = new BetxInterfaceProperties(Path.of("betx.yml"));
        Mockito.when(statusService.status()).thenReturn(new BetxInterfaceStatusView(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            null,
            Instant.parse("2026-06-18T10:00:00Z"),
            false
        ));
        Mockito.when(activityService.recent()).thenReturn(List.of(new BetxInterfaceActivityItem(
            "intent-1",
            "Real Madrid vs Barcelona",
            "Empate",
            BigDecimal.valueOf(3.2),
            BigDecimal.valueOf(5),
            "Realizada",
            null,
            Instant.parse("2026-06-18T09:01:00Z")
        )));
        BetxInterfaceController controller = new BetxInterfaceController(
            statusService,
            runtimeService,
            activityService,
            properties
        );

        assertThat(controller.status().status()).isEqualTo(InterfaceStatus.PAUSED);
        assertThat(controller.activity()).singleElement()
            .satisfies(item -> assertThat(item.event()).isEqualTo("Real Madrid vs Barcelona"));
    }

    @Test
    void activatesAndPausesThroughRuntimeService() {
        BetxInterfaceStatusService statusService = Mockito.mock(BetxInterfaceStatusService.class);
        BetxInterfaceRuntimeService runtimeService = Mockito.mock(BetxInterfaceRuntimeService.class);
        BetxInterfaceActivityService activityService = Mockito.mock(BetxInterfaceActivityService.class);
        BetxInterfaceProperties properties = new BetxInterfaceProperties(Path.of("betx.yml"));
        Mockito.when(statusService.status()).thenReturn(new BetxInterfaceStatusView(
            InterfaceStatus.ACTIVE,
            "BetX esta activo.",
            null,
            Instant.parse("2026-06-18T10:01:00Z"),
            false
        ));
        BetxInterfaceController controller = new BetxInterfaceController(
            statusService,
            runtimeService,
            activityService,
            properties
        );

        controller.activate();
        controller.pause();

        Mockito.verify(runtimeService).activate(new ConfigPath(Path.of("betx.yml")));
        Mockito.verify(runtimeService).pause();
    }
}
