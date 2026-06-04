package com.betx.domain.signal;

import java.util.Optional;

/** Strategy decision with an optional accepted signal. */
public record SignalDecision(boolean accepted, Optional<BetSignal> signal, String reason) {
    public SignalDecision {
        signal = signal == null ? Optional.empty() : signal;
        reason = reason == null ? "" : reason;
    }

    /** Creates an accepted decision. */
    public static SignalDecision accepted(BetSignal signal) {
        return new SignalDecision(true, Optional.of(signal), signal.reason());
    }

    /** Creates a rejected decision. */
    public static SignalDecision rejected(String reason) {
        return new SignalDecision(false, Optional.empty(), reason);
    }
}
