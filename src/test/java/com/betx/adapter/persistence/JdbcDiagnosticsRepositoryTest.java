package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetRecommendation;
import com.betx.application.BetRecommendationSource;
import com.betx.application.BetRecommendationStatus;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentSource;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.SelectionSide;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcDiagnosticsRepositoryTest {
    private static final Instant CUTOFF = Instant.parse("2026-06-24T11:53:02.338193Z");
    private static final Instant TO = Instant.parse("2026-06-24T23:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void countsPaperRecommendationCoverageFromPaperTradeWindowAndLinkedRecommendationStatus() {
        String databasePath = tempDir.resolve("diagnostics.db").toString();
        JdbcBetRecommendationRepository recommendationRepository = new JdbcBetRecommendationRepository(databasePath);
        JdbcPaperTradeRepository paperTradeRepository = new JdbcPaperTradeRepository(databasePath);
        recommendationRepository.save(
            databasePath,
            recommendation("rec-active-before-cutoff", "eval-active", "1.100", BetRecommendationStatus.ACTIVE, CUTOFF.minusSeconds(300))
        );
        recommendationRepository.save(
            databasePath,
            recommendation("rec-covered-before-cutoff", "eval-covered", "1.101", BetRecommendationStatus.COVERED, CUTOFF.minusSeconds(240))
        );
        recommendationRepository.save(
            databasePath,
            recommendation("rec-expired-before-cutoff", "eval-expired", "1.102", BetRecommendationStatus.EXPIRED, CUTOFF.minusSeconds(180))
        );

        paperTradeRepository.upsert(databasePath, paperTrade("historical-without-rec", "1.099", CUTOFF.minusSeconds(60), null));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-active", "1.100", CUTOFF.plusSeconds(1), "rec-active-before-cutoff"));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-covered", "1.101", CUTOFF.plusSeconds(2), "rec-covered-before-cutoff"));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-expired", "1.102", CUTOFF.plusSeconds(3), "rec-expired-before-cutoff"));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-broken", "1.103", CUTOFF.plusSeconds(4), "missing-rec"));

        var dataset = new JdbcDiagnosticsRepository().load(databasePath, CUTOFF, TO);
        var coverage = dataset.paperRecommendationCoverage();

        assertThat(coverage.paperTradesTotal()).isEqualTo(4);
        assertThat(coverage.paperTradesWithRecommendationId()).isEqualTo(4);
        assertThat(coverage.paperTradesWithoutRecommendationId()).isZero();
        assertThat(coverage.post23PaperTrades()).isEqualTo(4);
        assertThat(coverage.post23PaperTradesWithRecommendationId()).isEqualTo(4);
        assertThat(coverage.paperTradesWithRecommendationIdButMissingBetRecommendation()).isEqualTo(1);
        assertThat(coverage.paperTradesLinkedToCanonicalRecommendation()).isEqualTo(3);
        assertThat(coverage.paperTradesLinkedToActiveRecommendations()).isEqualTo(1);
        assertThat(coverage.paperTradesLinkedToCoveredRecommendations()).isEqualTo(1);
        assertThat(coverage.paperTradesLinkedToExpiredRecommendations()).isEqualTo(1);
    }

    @Test
    void countsRecommendationReadinessAcrossPaperLinksAndExactRealEquivalents() {
        String databasePath = tempDir.resolve("readiness.db").toString();
        JdbcBetRecommendationRepository recommendationRepository = new JdbcBetRecommendationRepository(databasePath);
        JdbcPaperTradeRepository paperTradeRepository = new JdbcPaperTradeRepository(databasePath);
        JdbcBetIntentRepository betIntentRepository = new JdbcBetIntentRepository(databasePath);
        recommendationRepository.save(databasePath, recommendation("rec-neither", "eval-neither", "1.200", BetRecommendationStatus.ACTIVE, CUTOFF));
        recommendationRepository.save(databasePath, recommendation("rec-paper", "eval-paper", "1.201", BetRecommendationStatus.ACTIVE, CUTOFF));
        recommendationRepository.save(databasePath, recommendation("rec-real", "eval-real", "1.202", BetRecommendationStatus.ACTIVE, CUTOFF));
        recommendationRepository.save(databasePath, recommendation("rec-both", "eval-both", "1.203", BetRecommendationStatus.COVERED, CUTOFF));

        paperTradeRepository.upsert(databasePath, paperTrade("paper-linked", "1.201", CUTOFF.plusSeconds(1), "rec-paper"));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-both", "1.203", CUTOFF.plusSeconds(2), "rec-both"));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-missing-id", "1.204", CUTOFF.plusSeconds(3), null));
        paperTradeRepository.upsert(databasePath, paperTrade("paper-broken", "1.205", CUTOFF.plusSeconds(4), "missing-rec"));
        betIntentRepository.save(databasePath, realBet("real-equivalent", "1.202", null));
        betIntentRepository.save(databasePath, realBet("real-both", "1.203", null));
        betIntentRepository.save(databasePath, realBet("real-unexpected-rec", "1.206", "rec-unexpected"));

        var dataset = new JdbcDiagnosticsRepository().load(databasePath, CUTOFF, TO);
        var readiness = dataset.recommendationReadiness();

        assertThat(readiness.totalCanonicalRecommendations()).isEqualTo(4);
        assertThat(readiness.activeRecommendations()).isEqualTo(3);
        assertThat(readiness.coveredRecommendations()).isEqualTo(1);
        assertThat(readiness.expiredRecommendations()).isZero();
        assertThat(readiness.recommendationsWithPaperTrades()).isEqualTo(2);
        assertThat(readiness.recommendationsWithoutPaperTrades()).isEqualTo(2);
        assertThat(readiness.recommendationsWithRealEquivalentBet()).isEqualTo(2);
        assertThat(readiness.recommendationsWithoutRealEquivalentBet()).isEqualTo(2);
        assertThat(readiness.recommendationsWithBothPaperAndRealEquivalent()).isEqualTo(1);
        assertThat(readiness.recommendationsWithPaperOnly()).isEqualTo(1);
        assertThat(readiness.recommendationsWithRealOnly()).isEqualTo(1);
        assertThat(readiness.recommendationsWithNeitherPaperNorReal()).isEqualTo(1);
        assertThat(readiness.paperTradesMissingRecommendationIdPost23()).isEqualTo(1);
        assertThat(readiness.brokenPaperRecommendationJoins()).isEqualTo(1);
        assertThat(readiness.realBetsWithRecommendationId()).isEqualTo(1);
        assertThat(readiness.realBetsMissingRecommendationId()).isEqualTo(2);
        assertThat(readiness.realBetsTotal()).isEqualTo(3);
        assertThat(readiness.post25RealBets()).isEqualTo(3);
        assertThat(readiness.post25RealBetsWithRecommendationId()).isEqualTo(1);
        assertThat(readiness.post25RealBetsWithoutRecommendationId()).isEqualTo(2);
        assertThat(readiness.realBetsWithRecommendationIdButMissingBetRecommendation()).isEqualTo(1);
        assertThat(readiness.readyForRecommendationIdMatching()).isEqualTo("NO");
    }

    @Test
    void countsRealRecommendationCoverageByBetIntentCreationTimeAndLinkedStatus() {
        String databasePath = tempDir.resolve("real-recommendation-coverage.db").toString();
        JdbcBetRecommendationRepository recommendationRepository = new JdbcBetRecommendationRepository(databasePath);
        JdbcBetIntentRepository betIntentRepository = new JdbcBetIntentRepository(databasePath);
        recommendationRepository.save(
            databasePath,
            recommendation("rec-active-before-real-cutoff", "eval-active", "1.300", BetRecommendationStatus.ACTIVE, CUTOFF.minusSeconds(300))
        );
        recommendationRepository.save(
            databasePath,
            recommendation("rec-covered-before-real-cutoff", "eval-covered", "1.301", BetRecommendationStatus.COVERED, CUTOFF.minusSeconds(240))
        );
        recommendationRepository.save(
            databasePath,
            recommendation("rec-expired-before-real-cutoff", "eval-expired", "1.302", BetRecommendationStatus.EXPIRED, CUTOFF.minusSeconds(180))
        );

        betIntentRepository.save(databasePath, realBet("historical-without-rec", "1.299", null, CUTOFF.minusSeconds(60)));
        betIntentRepository.save(databasePath, realBet(
            "real-active",
            "1.300",
            "rec-active-before-real-cutoff",
            CUTOFF.plusSeconds(1)
        ));
        betIntentRepository.save(databasePath, realBet(
            "real-covered",
            "1.301",
            "rec-covered-before-real-cutoff",
            CUTOFF.plusSeconds(2)
        ));
        betIntentRepository.save(databasePath, realBet(
            "real-expired",
            "1.302",
            "rec-expired-before-real-cutoff",
            CUTOFF.plusSeconds(3)
        ));
        betIntentRepository.save(databasePath, realBet("real-broken", "1.303", "missing-rec", CUTOFF.plusSeconds(4)));
        betIntentRepository.save(databasePath, realBet("real-missing-post25", "1.304", null, CUTOFF.plusSeconds(5)));

        var dataset = new JdbcDiagnosticsRepository().load(databasePath, CUTOFF.minusSeconds(120), TO);
        var readiness = dataset.recommendationReadiness();

        assertThat(readiness.realBetsTotal()).isEqualTo(6);
        assertThat(readiness.realBetsWithRecommendationId()).isEqualTo(4);
        assertThat(readiness.realBetsMissingRecommendationId()).isEqualTo(2);
        assertThat(readiness.post25RealBets()).isEqualTo(5);
        assertThat(readiness.post25RealBetsWithRecommendationId()).isEqualTo(4);
        assertThat(readiness.post25RealBetsWithoutRecommendationId()).isEqualTo(1);
        assertThat(readiness.realBetsWithRecommendationIdButMissingBetRecommendation()).isEqualTo(1);
        assertThat(readiness.realBetsLinkedToCanonicalRecommendation()).isEqualTo(3);
        assertThat(readiness.realBetsLinkedToActiveRecommendations()).isEqualTo(1);
        assertThat(readiness.realBetsLinkedToCoveredRecommendations()).isEqualTo(1);
        assertThat(readiness.realBetsLinkedToExpiredRecommendations()).isEqualTo(1);
    }

    private static BetRecommendation recommendation(
        String id,
        String evaluationId,
        String marketId,
        BetRecommendationStatus status,
        Instant observedAt
    ) {
        return new BetRecommendation(
            id,
            evaluationId,
            "betfair",
            marketId,
            58805L,
            SelectionSide.DRAW,
            "Team A v Team B",
            "The Draw",
            "Cup",
            Instant.parse("2026-06-25T20:00:00Z"),
            "value-football",
            new BigDecimal("3.50"),
            observedAt,
            observedAt,
            BetRecommendationSource.SHADOW,
            status,
            observedAt,
            null,
            null,
            null,
            null
        );
    }

    private static PaperTrade paperTrade(String id, String marketId, Instant recommendedAt, String recommendationId) {
        return new PaperTrade(
            id,
            "betfair",
            marketId,
            58805L,
            "Team A v Team B",
            "Match Odds",
            "Cup",
            Instant.parse("2026-06-25T20:00:00Z"),
            "The Draw",
            BetSide.BACK,
            PaperTradeStatus.EXECUTED,
            recommendedAt,
            new BigDecimal("3.50"),
            new BigDecimal("3.50"),
            recommendedAt.plusSeconds(1),
            new BigDecimal("3.50"),
            true,
            null,
            null,
            null,
            null,
            new BigDecimal("5.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            true,
            recommendationId
        );
    }

    private static BetIntent realBet(String id, String marketId, String recommendationId) {
        return realBet(id, marketId, recommendationId, CUTOFF.plusSeconds(5));
    }

    private static BetIntent realBet(String id, String marketId, String recommendationId, Instant createdAt) {
        return new BetIntent(
            id,
            BetIntentSource.AUTOMATIC,
            "betfair",
            marketId,
            58805L,
            "Team A v Team B",
            "Match Odds",
            "The Draw",
            "Cup",
            SelectionSide.DRAW,
            "value-football",
            BetSide.BACK,
            "liquidity_ok",
            new BigDecimal("3.50"),
            BigDecimal.ONE,
            new BigDecimal("20.00"),
            null,
            null,
            null,
            BigDecimal.ONE,
            "accepted",
            "bet-" + id,
            null,
            null,
            null,
            BetIntentStage.EXECUTED,
            createdAt,
            createdAt.plusSeconds(1),
            "eval-" + id,
            recommendationId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
