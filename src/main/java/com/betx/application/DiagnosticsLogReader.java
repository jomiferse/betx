package com.betx.application;

import java.nio.file.Path;
import java.time.Instant;

public interface DiagnosticsLogReader {
    DiagnosticsLogSummary read(Path logsDir, Instant from, Instant to);
}
