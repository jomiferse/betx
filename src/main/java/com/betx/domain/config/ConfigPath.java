package com.betx.domain.config;

import java.nio.file.Path;

public record ConfigPath(Path value) {
    public ConfigPath {
        if (value == null) {
            throw new IllegalArgumentException("Config path is required.");
        }
    }
}
