package com.betx.adapter.config;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.common.ConfigException;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class YamlBetxConfigRepository implements BetxConfigRepository {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    @Override
    public BetxConfig load(ConfigPath path) {
        if (!Files.exists(path.value())) {
            throw new ConfigException("Configuration file not found: " + path.value());
        }

        try {
            return mapper.readValue(path.value().toFile(), BetxConfig.class);
        } catch (IOException exc) {
            throw new ConfigException("Configuration file is invalid: " + path.value(), exc);
        }
    }

    @Override
    public boolean writeDefault(ConfigPath path, boolean force) {
        try {
            if (Files.exists(path.value()) && !force) {
                return false;
            }
            Files.writeString(path.value(), DefaultConfigTemplates.defaultConfig());
            return true;
        } catch (IOException exc) {
            throw new ConfigException("Could not write config file: " + path.value(), exc);
        }
    }

    @Override
    public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        Map<String, Object> raw = readRaw(path);
        Object existingTelegram = raw.get("telegram");
        Map<String, Object> telegram = new LinkedHashMap<>();
        if (existingTelegram instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> telegram.put(String.valueOf(key), value));
        }
        telegram.putAll(fields);
        raw.put("telegram", telegram);
        writeRaw(path, raw);
    }

    private Map<String, Object> readRaw(ConfigPath path) {
        try {
            return mapper.readValue(path.value().toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException exc) {
            throw new ConfigException("Could not read config file: " + path.value(), exc);
        }
    }

    private void writeRaw(ConfigPath path, Map<String, Object> raw) {
        try {
            mapper.writeValue(path.value().toFile(), raw);
        } catch (IOException exc) {
            throw new ConfigException("Could not write config file: " + path.value(), exc);
        }
    }
}
