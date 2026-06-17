package com.betx.application;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record BetxInterfaceProperties(Path configPath) {
    public BetxInterfaceProperties(@Value("${betx.interface.config:betx.yml}") Path configPath) {
        this.configPath = configPath == null ? Path.of("betx.yml") : configPath;
    }
}
