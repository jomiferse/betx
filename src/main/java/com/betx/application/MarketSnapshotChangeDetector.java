package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Compares consecutive snapshots for one market runner. */
@Component
public class MarketSnapshotChangeDetector {
    public Optional<MarketSnapshotChange> compare(MarketSnapshot previous, MarketSnapshot current) {
        if (previous == null || current == null) {
            return Optional.empty();
        }
        MarketSnapshotChange change = new MarketSnapshotChange(
            previous,
            current,
            compareValue(previous.bestBackPrice(), current.bestBackPrice()),
            compareValue(previous.bestLayPrice(), current.bestLayPrice()),
            compareValue(previous.spread(), current.spread()),
            compareValue(previous.liquidity(), current.liquidity())
        );
        if (!hasChanged(change.back()) && !hasChanged(change.lay()) && !hasChanged(change.spread()) && !hasChanged(change.liquidity())) {
            return Optional.empty();
        }
        return Optional.of(change);
    }

    private NumericChange compareValue(BigDecimal previous, BigDecimal current) {
        BigDecimal absoluteDelta = previous == null || current == null ? null : current.subtract(previous);
        BigDecimal percentageDelta = null;
        if (absoluteDelta != null && previous.compareTo(BigDecimal.ZERO) != 0) {
            percentageDelta = absoluteDelta
                .divide(previous, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(8, RoundingMode.HALF_UP);
        }
        return new NumericChange(previous, current, absoluteDelta, percentageDelta);
    }

    private boolean hasChanged(NumericChange change) {
        if (change.previous() == null || change.current() == null) {
            return !Objects.equals(change.previous(), change.current());
        }
        return change.previous().compareTo(change.current()) != 0;
    }
}
