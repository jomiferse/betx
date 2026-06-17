package com.betx.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BetxInterfaceStaticPageTest {
    @Test
    void routesInterfaceHomeToStaticPage() {
        BetxInterfacePageController controller = new BetxInterfacePageController();

        assertThat(controller.home()).isEqualTo("redirect:/interface/");
        assertThat(controller.interfaceHome()).isEqualTo("forward:/interface/index.html");
    }

    @Test
    void pageUsesOnlyCommercialInterfaceLanguage() throws IOException {
        String html = resource("/static/interface/index.html");
        String js = resource("/static/interface/app.js");

        assertThat(html)
            .contains("BetX")
            .contains("Activar BetX")
            .contains("Pausar BetX")
            .doesNotContain("paper")
            .doesNotContain("backtest")
            .doesNotContain("runner")
            .doesNotContain("snapshot")
            .doesNotContain("gateway");
        assertThat(js)
            .contains("/api/interface/status")
            .contains("/api/interface/activity")
            .doesNotContain("paper")
            .doesNotContain("backtest");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
