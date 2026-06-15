package com.betx.application;

import com.betx.domain.signal.RunnerType;

/** Sample row showing how a prospective paper-trading runner was classified. */
public record PaperTradeRunnerClassificationDiagnostic(
    String marketId,
    String marketName,
    long selectionId,
    String runnerName,
    String normalizedRunnerName,
    RunnerType inferredRunnerType,
    boolean draw
) {
}
