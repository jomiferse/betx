package com.betx.application;

import com.betx.domain.signal.BetSignal;

/** Formats betting signals for human-visible channels. */
public class DryRunSignalFormatter {
    /** Formats one signal as the terminal line expected by the CLI. */
    public String format(BetSignal signal) {
        return "SIGNAL | "
            + "exchange="
            + signal.exchange()
            + " | "
            + signal.side()
            + " | marketId="
            + signal.marketId()
            + " | selectionId="
            + signal.selectionId()
            + " | odds="
            + signal.odds().stripTrailingZeros().toPlainString()
            + " | stake="
            + signal.stake().stripTrailingZeros().toPlainString();
    }
}
