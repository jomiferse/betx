package com.betx.application;

import com.betx.domain.config.ConfigPath;

/** Generates a read-only report from persisted real betting evidence. */
public interface GenerateRealBettingReportUseCase {
    RealBettingReport generate(ConfigPath configPath);
}
