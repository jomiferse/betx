package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Formats snapshot movements for CLI output. */
@Component
public class MarketSnapshotChangeFormatter {
    private static final BigDecimal ODDS_CHANGE_THRESHOLD = BigDecimal.valueOf(1.0);
    private static final BigDecimal LIQUIDITY_CHANGE_THRESHOLD = BigDecimal.valueOf(2.0);

    public String format(MarketSnapshotChange change) {
        return "SNAPSHOT CHANGE | exchange=" + change.current().exchange()
            + " | marketId=" + change.current().marketId()
            + " | selectionId=" + change.current().selectionId()
            + " | back=" + format(change.back())
            + " | lay=" + format(change.lay())
            + " | spread=" + format(change.spread())
            + " | liquidity=" + format(change.liquidity());
    }

    public boolean isRelevant(MarketSnapshotChange change) {
        return percentMagnitudeAtLeast(change.back(), ODDS_CHANGE_THRESHOLD)
            || percentMagnitudeAtLeast(change.lay(), ODDS_CHANGE_THRESHOLD)
            || percentMagnitudeAtLeast(change.liquidity(), LIQUIDITY_CHANGE_THRESHOLD);
    }

    private String format(NumericChange change) {
        return value(change.previous()) + " -> " + value(change.current()) + " (" + percent(change.percentageDelta()) + ")";
    }

    private String value(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private String percent(BigDecimal percentage) {
        if (percentage == null) {
            return "n/a";
        }
        return percentage.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private boolean percentMagnitudeAtLeast(NumericChange change, BigDecimal threshold) {
        if (change.percentageDelta() == null) {
            return false;
        }
        return change.percentageDelta().abs().compareTo(threshold) >= 0;
    }
}
