package com.betx.application.port.out;

import com.betx.application.PaperSignalEvaluation;
import java.util.List;

/** Stores paper-trading analyzer evaluations for accepted and rejected runners. */
public interface PaperSignalEvaluationRepository {
    void save(String databasePath, PaperSignalEvaluation evaluation);

    List<PaperSignalEvaluation> listLatest(String databasePath, int limit);
}
