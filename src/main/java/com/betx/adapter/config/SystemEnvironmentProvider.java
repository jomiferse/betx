package com.betx.adapter.config;

import com.betx.application.port.out.EnvironmentProvider;
import org.springframework.stereotype.Component;

@Component
public class SystemEnvironmentProvider implements EnvironmentProvider {
    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}
