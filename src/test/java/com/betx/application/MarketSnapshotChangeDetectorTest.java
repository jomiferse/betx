package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketSnapshotChangeDetectorTest {
    private final MarketSnapshotChangeDetector detector = new MarketSnapshotChangeDetector();

    @Test
    void doesNotEmitChangeWithoutPreviousSnapshot() {
        assertThat(detector.compare(null, snapshot(BigDecimal.valueOf(2.44), BigDecimal.valueOf(2.52), BigDecimal.valueOf(0.0328), BigDecimal.valueOf(1_600))))
            .isEmpty();
    }

    @Test
    void calculatesAbsoluteAndPercentageDeltas() {
        MarketSnapshot previous = snapshot(BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.60), BigDecimal.valueOf(0.04), BigDecimal.valueOf(1_500));
        MarketSnapshot current = snapshot(BigDecimal.valueOf(2.44), BigDecimal.valueOf(2.52), BigDecimal.valueOf(0.0328), BigDecimal.valueOf(1_650));

        var change = detector.compare(previous, current).orElseThrow();

        assertThat(change.back().previous()).isEqualByComparingTo("2.50");
        assertThat(change.back().current()).isEqualByComparingTo("2.44");
        assertThat(change.back().absoluteDelta()).isEqualByComparingTo("-0.06");
        assertThat(change.back().percentageDelta()).isEqualByComparingTo("-2.40000000");
        assertThat(change.lay().percentageDelta()).isEqualByComparingTo("-3.07692308");
        assertThat(change.spread().percentageDelta()).isEqualByComparingTo("-18.00000000");
        assertThat(change.liquidity().percentageDelta()).isEqualByComparingTo("10.00000000");
    }

    @Test
    void handlesNullValuesWithoutFailing() {
        MarketSnapshot previous = snapshot(null, BigDecimal.valueOf(2.60), null, BigDecimal.ZERO);
        MarketSnapshot current = snapshot(BigDecimal.valueOf(2.44), null, BigDecimal.valueOf(0.0328), BigDecimal.valueOf(1_650));

        var change = detector.compare(previous, current).orElseThrow();

        assertThat(change.back().previous()).isNull();
        assertThat(change.back().percentageDelta()).isNull();
        assertThat(change.lay().current()).isNull();
        assertThat(change.liquidity().percentageDelta()).isNull();
    }

    private MarketSnapshot snapshot(BigDecimal back, BigDecimal lay, BigDecimal spread, BigDecimal liquidity) {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            back,
            lay,
            spread,
            liquidity
        );
    }
}
