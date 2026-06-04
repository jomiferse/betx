package com.betx.application.port.out;

import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.util.Map;

public interface BetxConfigRepository {
    BetxConfig load(ConfigPath path);

    boolean writeDefault(ConfigPath path, boolean force);

    void saveTelegramFields(ConfigPath path, Map<String, Object> fields);
}
