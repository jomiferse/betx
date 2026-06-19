package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import java.time.Instant;

public interface DiagnosticsRepository {
    DiagnosticsDataset load(String databasePath, Instant from, Instant to);

    DiagnosticsPeriod findDefaultPeriod(String databasePath);
}
