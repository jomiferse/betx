package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticFindingSeverity;

public record DiagnosticFinding(
    DiagnosticFindingSeverity severity,
    String code,
    String message,
    long observations
) {
}
