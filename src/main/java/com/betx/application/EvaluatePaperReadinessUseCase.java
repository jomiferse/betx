package com.betx.application;

import com.betx.domain.config.ConfigPath;

public interface EvaluatePaperReadinessUseCase {
    PaperReadinessResult evaluate(ConfigPath configPath, String strategy);
}
