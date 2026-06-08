package com.betx.application;

import com.betx.application.port.out.ExternalMatchIntelligenceGateway;

final class NoopExternalMatchIntelligenceGateway implements ExternalMatchIntelligenceGateway {
    @Override
    public MatchIntelligenceAssessment assess(MatchIntelligenceRequest request) {
        return MatchIntelligenceAssessment.unavailable(
            request.analysis().exchange(),
            request.analysis().marketId(),
            request.analysis().selectionId(),
            "External intelligence is not configured."
        );
    }
}
