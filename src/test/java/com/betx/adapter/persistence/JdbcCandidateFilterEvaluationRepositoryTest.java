package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.CandidateFilterDecision;
import com.betx.application.CandidateFilterDecisionReason;
import com.betx.application.CandidateFilterEvaluation;
import com.betx.application.CandidateFilterName;
import com.betx.application.CandidateFilterSource;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcCandidateFilterEvaluationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void savesShadowEvaluationWithRecommendationId() {
        String databasePath = tempDir.resolve("candidate-filters.db").toString();
        JdbcCandidateFilterEvaluationRepository repository = new JdbcCandidateFilterEvaluationRepository(databasePath);
        CandidateFilterEvaluation evaluation = evaluation(
            "rec-1",
            CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS,
            CandidateFilterSource.RECOMMENDATION,
            CandidateFilterDecision.WOULD_FILTER
        );

        repository.upsert(databasePath, evaluation);

        assertThat(repository.list(databasePath, null, null)).singleElement().satisfies(saved -> {
            assertThat(saved.recommendationId()).isEqualTo("rec-1");
            assertThat(saved.filterName()).isEqualTo(CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS);
            assertThat(saved.decision()).isEqualTo(CandidateFilterDecision.WOULD_FILTER);
            assertThat(saved.observedCount()).isEqualTo(1);
        });
    }

    @Test
    void reEvaluationUpdatesSameRecommendationFilterAndSourceInsteadOfDuplicating() {
        String databasePath = tempDir.resolve("candidate-filters-idempotent.db").toString();
        JdbcCandidateFilterEvaluationRepository repository = new JdbcCandidateFilterEvaluationRepository(databasePath);
        CandidateFilterEvaluation first = evaluation(
            "rec-1",
            CandidateFilterName.EXCLUDE_ODDS_4_PLUS,
            CandidateFilterSource.RECOMMENDATION,
            CandidateFilterDecision.WOULD_PASS
        );
        CandidateFilterEvaluation second = first.withLatest(
            CandidateFilterDecision.WOULD_FILTER,
            CandidateFilterDecisionReason.ODDS_4_PLUS,
            new BigDecimal("4.20"),
            Instant.parse("2026-06-22T08:35:00Z")
        );

        repository.upsert(databasePath, first);
        repository.upsert(databasePath, second);

        assertThat(repository.list(databasePath, null, null)).singleElement().satisfies(saved -> {
            assertThat(saved.decision()).isEqualTo(CandidateFilterDecision.WOULD_FILTER);
            assertThat(saved.reason()).isEqualTo(CandidateFilterDecisionReason.ODDS_4_PLUS);
            assertThat(saved.odds()).isEqualByComparingTo("4.20");
            assertThat(saved.observedCount()).isEqualTo(2);
            assertThat(saved.lastEvaluatedAt()).isEqualTo(Instant.parse("2026-06-22T08:35:00Z"));
        });
    }

    @Test
    void allowsMultipleSourcesAndFiltersForSameRecommendation() {
        String databasePath = tempDir.resolve("candidate-filters-multiple.db").toString();
        JdbcCandidateFilterEvaluationRepository repository = new JdbcCandidateFilterEvaluationRepository(databasePath);

        repository.upsert(databasePath, evaluation("rec-1", CandidateFilterName.EXCLUDE_ODDS_4_PLUS, CandidateFilterSource.RECOMMENDATION, CandidateFilterDecision.WOULD_PASS));
        repository.upsert(databasePath, evaluation("rec-1", CandidateFilterName.EXCLUDE_ODDS_4_PLUS, CandidateFilterSource.REAL, CandidateFilterDecision.WOULD_PASS));
        repository.upsert(databasePath, evaluation("rec-1", CandidateFilterName.EXCLUDE_DRAW_AND_AWAY, CandidateFilterSource.RECOMMENDATION, CandidateFilterDecision.WOULD_FILTER));

        assertThat(repository.list(databasePath, null, null)).hasSize(3);
    }

    private static CandidateFilterEvaluation evaluation(
        String recommendationId,
        CandidateFilterName filterName,
        CandidateFilterSource source,
        CandidateFilterDecision decision
    ) {
        return new CandidateFilterEvaluation(
            recommendationId + "-" + filterName.name() + "-" + source.name(),
            recommendationId,
            "betfair|1.234|42|DRAW|value-football",
            filterName,
            decision,
            decision == CandidateFilterDecision.WOULD_FILTER ? CandidateFilterDecisionReason.SELECTION_SIDE_DRAW : CandidateFilterDecisionReason.PASSED,
            SelectionSide.DRAW,
            new BigDecimal("3.50"),
            "value-football",
            source,
            Instant.parse("2026-06-22T08:25:00Z"),
            Instant.parse("2026-06-22T08:25:00Z"),
            Instant.parse("2026-06-22T08:25:00Z"),
            1
        );
    }
}
