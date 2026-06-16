package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.PaperSignalEvaluation;
import com.betx.application.PaperTradeAnalyzerRejectionReason;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcPaperSignalEvaluationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndListsLatestPaperSignalEvaluations() {
        Path database = tempDir.resolve("betx.db");
        JdbcPaperSignalEvaluationRepository repository = new JdbcPaperSignalEvaluationRepository(database.toString());
        PaperSignalEvaluation accepted = evaluation(
            Instant.parse("2026-06-15T10:00:00Z"),
            RecommendationType.BET,
            PaperTradeAnalyzerRejectionReason.ACCEPTED,
            RunnerType.DRAW
        );
        PaperSignalEvaluation rejected = evaluation(
            Instant.parse("2026-06-15T10:01:00Z"),
            RecommendationType.NO_BET,
            PaperTradeAnalyzerRejectionReason.NOT_DRAW,
            RunnerType.HOME
        );

        repository.save(database.toString(), accepted);
        repository.save(database.toString(), rejected);

        assertThat(repository.listLatest(database.toString(), 10))
            .containsExactly(rejected, accepted);
        assertThat(repository.listLatest(database.toString(), 1))
            .containsExactly(rejected);
    }

    private static PaperSignalEvaluation evaluation(
        Instant observedAt,
        RecommendationType recommendation,
        PaperTradeAnalyzerRejectionReason analyzerReason,
        RunnerType runnerType
    ) {
        return new PaperSignalEvaluation(
            observedAt,
            "betfair",
            "market-1",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            runnerType == RunnerType.DRAW ? 2L : 1L,
            runnerType == RunnerType.DRAW ? "Draw" : "Team A",
            runnerType,
            recommendation,
            recommendation == RecommendationType.BET ? 80 : 0,
            recommendation == RecommendationType.BET ? "High confidence" : "No bet",
            recommendation == RecommendationType.BET ? "dry_run_only" : "not_draw",
            new BigDecimal("3.70"),
            new BigDecimal("3.80"),
            new BigDecimal("0.04"),
            new BigDecimal("1200"),
            new BigDecimal("-1.00"),
            null,
            new BigDecimal("2.50"),
            analyzerReason
        );
    }
}
