package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.common.ConfigException;
import com.betx.domain.config.ConfigPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class InitializeProjectService {
    private final BetxConfigRepository configRepository;

    public InitializeProjectService(BetxConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public InitResult initialize(Path baseDirectory, boolean force) {
        boolean configWritten = configRepository.writeDefault(new ConfigPath(baseDirectory.resolve("betx.yml")), force);
        createDirectory(baseDirectory.resolve("data"));
        createDirectory(baseDirectory.resolve("models"));
        return new InitResult(configWritten, true, true);
    }

    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exc) {
            throw new ConfigException("Could not create directory: " + path, exc);
        }
    }

    public record InitResult(boolean configWritten, boolean dataDirectoryReady, boolean modelsDirectoryReady) {
    }
}
