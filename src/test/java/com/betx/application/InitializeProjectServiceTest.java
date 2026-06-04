package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitializeProjectServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsConfigAndLocalDirectories() {
        RecordingConfigRepository repository = new RecordingConfigRepository(true);
        InitializeProjectService service = new InitializeProjectService(repository);

        InitializeProjectService.InitResult result = service.initialize(tempDir, false);

        assertThat(result.configWritten()).isTrue();
        assertThat(result.dataDirectoryReady()).isTrue();
        assertThat(result.modelsDirectoryReady()).isTrue();
        assertThat(repository.path()).isEqualTo(new ConfigPath(tempDir.resolve("betx.yml")));
        assertThat(repository.force()).isFalse();
        assertThat(Files.isDirectory(tempDir.resolve("data"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("models"))).isTrue();
    }

    @Test
    void reportsConfigNotWrittenWhenRepositoryKeepsExistingFile() {
        RecordingConfigRepository repository = new RecordingConfigRepository(false);
        InitializeProjectService service = new InitializeProjectService(repository);

        InitializeProjectService.InitResult result = service.initialize(tempDir, true);

        assertThat(result.configWritten()).isFalse();
        assertThat(repository.force()).isTrue();
    }

    private static final class RecordingConfigRepository implements BetxConfigRepository {
        private final boolean writeResult;
        private ConfigPath path;
        private boolean force;

        private RecordingConfigRepository(boolean writeResult) {
            this.writeResult = writeResult;
        }

        @Override
        public BetxConfig load(ConfigPath path) {
            return BetxConfig.defaults();
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            this.path = path;
            this.force = force;
            return writeResult;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }

        private ConfigPath path() {
            return path;
        }

        private boolean force() {
            return force;
        }
    }
}
