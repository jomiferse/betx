package com.betx.application;

import java.math.BigDecimal;
import java.util.List;

public record DiagnosticsStakeSizingScenario(
    String scenarioName,
    BigDecimal baseStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    List<DiagnosticsStakeSizingScenarioPolicyResult> policyResults
) {
    public DiagnosticsStakeSizingScenario {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName is required.");
        }
        policyResults = policyResults == null ? List.of() : List.copyOf(policyResults);
    }
}
