package com.betx.application;

import com.betx.domain.config.IntelligenceConfig;
import com.betx.domain.signal.RunnerAnalysis;

public record MatchIntelligenceRequest(
    IntelligenceConfig config,
    RunnerAnalysis analysis,
    boolean autoBettingEnabled,
    boolean requestConfirmation
) {
}
