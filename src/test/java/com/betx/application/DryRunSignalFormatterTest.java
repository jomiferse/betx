package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.BetSignal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DryRunSignalFormatterTest {
    @Test
    void includesExchangeInSignalLine() {
        BetSignal signal = new BetSignal(
            "betfair",
            "1.234",
            42L,
            BetSide.BACK,
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(5),
            "reason",
            "dry-run"
        );

        assertThat(new DryRunSignalFormatter().format(signal))
            .isEqualTo("SIGNAL DRY-RUN | exchange=betfair | BACK | marketId=1.234 | selectionId=42 | odds=2.5 | stake=5");
    }
}
