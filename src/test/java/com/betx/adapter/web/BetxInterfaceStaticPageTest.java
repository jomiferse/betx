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
        String frontendSource = frontendSource("frontend/src");
        String styles = java.nio.file.Files.readString(java.nio.file.Path.of("frontend/src/styles.css"), StandardCharsets.UTF_8);

        assertThat(html)
            .contains("BetX")
            .doesNotContain("paper")
            .doesNotContain("backtest")
            .doesNotContain("runner")
            .doesNotContain("snapshot")
            .doesNotContain("gateway");
        assertThat(frontendSource)
            .contains("API_ROOT = \"/api/v1/dashboard\"")
            .contains("BetX Dashboard")
            .contains("Performance, trades and risk analytics")
            .contains("getDashboardData")
            .doesNotContain("setInterval")
            .doesNotContain("Activar BetX")
            .doesNotContain("Pausar BetX")
            .doesNotContain("/api/interface/status")
            .doesNotContain("paper")
            .doesNotContain("backtest");
        assertThat(styles)
            .contains(".chart-tooltip.floating")
            .contains("z-index: 20;");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String frontendSource(String directory) throws IOException {
        StringBuilder content = new StringBuilder();
        try (var paths = java.nio.file.Files.walk(java.nio.file.Path.of(directory))) {
            paths.filter(java.nio.file.Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".tsx"))
                .filter(path -> !path.toString().endsWith(".test.ts"))
                .filter(path -> !path.toString().endsWith(".test.tsx"))
                .sorted()
                .forEach(path -> {
                    try {
                        content.append(java.nio.file.Files.readString(path, StandardCharsets.UTF_8));
                    } catch (IOException exc) {
                        throw new java.io.UncheckedIOException(exc);
                    }
                });
        } catch (java.io.UncheckedIOException exc) {
            throw exc.getCause();
        }
        return content.toString();
    }
}
