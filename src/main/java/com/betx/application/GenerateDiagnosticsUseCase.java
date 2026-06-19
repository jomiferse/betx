package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsRequest;

public interface GenerateDiagnosticsUseCase {
    DiagnosticsReport generate(DiagnosticsRequest request);
}
