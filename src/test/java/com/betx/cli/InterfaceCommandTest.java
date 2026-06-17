package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetxInterfaceLauncher;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterfaceCommandTest {
    @Test
    void launchesInterfaceWithDefaults() {
        RecordingLauncher launcher = new RecordingLauncher(0);
        InterfaceCommand command = new InterfaceCommand(launcher);

        int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(launcher.request.configPath()).isEqualTo(Path.of("betx.yml"));
        assertThat(launcher.request.port()).isEqualTo(8080);
        assertThat(launcher.request.openBrowser()).isTrue();
    }

    @Test
    void launchesInterfaceWithCustomOptions() {
        RecordingLauncher launcher = new RecordingLauncher(0);
        InterfaceCommand command = new InterfaceCommand(launcher);
        command.configPath = Path.of("custom.yml");
        command.port = 9090;
        command.noBrowser = true;

        int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(launcher.request.configPath()).isEqualTo(Path.of("custom.yml"));
        assertThat(launcher.request.port()).isEqualTo(9090);
        assertThat(launcher.request.openBrowser()).isFalse();
    }

    private static final class RecordingLauncher extends BetxInterfaceLauncher {
        private final int exitCode;
        private LaunchRequest request;

        private RecordingLauncher(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public int launch(LaunchRequest request) {
            this.request = request;
            return exitCode;
        }
    }
}
