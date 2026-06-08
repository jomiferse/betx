package com.betx.application.port.out;

import com.betx.application.MatchIntelligenceAssessment;
import com.betx.application.MatchIntelligenceRequest;

public interface ExternalMatchIntelligenceGateway {
    MatchIntelligenceAssessment assess(MatchIntelligenceRequest request);
}
