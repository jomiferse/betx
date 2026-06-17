package com.betx.cli;

import com.betx.application.BetxInterfaceLauncher;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "interface", description = "Start the BetX user interface.")
public class InterfaceCommand implements Callable<Integer> {
    private final BetxInterfaceLauncher launcher;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--port", defaultValue = "8080", description = "Local web port.")
    int port;

    @Option(names = "--no-browser", description = "Do not open the browser automatically.")
    boolean noBrowser;

    public InterfaceCommand(BetxInterfaceLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Integer call() {
        return launcher.launch(new BetxInterfaceLauncher.LaunchRequest(configPath, port, !noBrowser));
    }
}
