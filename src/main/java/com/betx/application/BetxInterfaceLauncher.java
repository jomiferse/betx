package com.betx.application;

import com.betx.BetxApplication;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class BetxInterfaceLauncher {
    public int launch(LaunchRequest request) {
        String url = "http://localhost:" + request.port() + "/interface/";
        SpringApplicationBuilder builder = new SpringApplicationBuilder(BetxApplication.class)
            .web(WebApplicationType.SERVLET)
            .bannerMode(org.springframework.boot.Banner.Mode.OFF)
            .properties(Map.of(
                "betx.interface.enabled", "true",
                "betx.interface.config", request.configPath().toString(),
                "server.port", String.valueOf(request.port()),
                "spring.main.log-startup-info", "false"
            ));
        ConfigurableApplicationContext context = builder.run();
        System.out.println("BetX interface available at " + url);
        openBrowser(url, request.openBrowser());
        context.registerShutdownHook();
        try {
            Thread.currentThread().join();
            return 0;
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            context.close();
            return 130;
        }
    }

    private void openBrowser(String url, boolean enabled) {
        if (!enabled || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (RuntimeException | java.io.IOException exc) {
            System.out.println("Could not open browser automatically. Open " + url);
        }
    }

    public record LaunchRequest(Path configPath, int port, boolean openBrowser) {
        public LaunchRequest {
            configPath = configPath == null ? Path.of("betx.yml") : configPath;
            port = port <= 0 ? 8080 : port;
        }
    }
}
