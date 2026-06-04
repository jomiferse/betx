package com.betx.domain.config;

public record StorageConfig(String type, String path) {
    public StorageConfig {
        type = type == null || type.isBlank() ? "sqlite" : type;
        path = path == null || path.isBlank() ? "./data/betx.db" : path;
    }
}
