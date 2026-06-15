package com.betx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BetxApplicationTest {
    @Test
    void returnsCommandExitCodeWithoutSpringExitCodeLookup(@TempDir Path tempDir) {
        int exitCode = BetxApplication.runForExitCode(new String[] {
            "--config",
            tempDir.resolve("missing.yml").toString(),
            "--unknown-option"
        });

        assertThat(exitCode).isNotZero();
    }
}
