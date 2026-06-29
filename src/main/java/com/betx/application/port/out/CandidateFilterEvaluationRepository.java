package com.betx.application.port.out;

import com.betx.application.CandidateFilterEvaluation;
import java.time.Instant;
import java.util.List;

public interface CandidateFilterEvaluationRepository {
    void upsert(String databasePath, CandidateFilterEvaluation evaluation);

    List<CandidateFilterEvaluation> list(String databasePath, Instant from, Instant to);
}
