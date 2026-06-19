package com.betx.application;

import java.time.Instant;

public record DiagnosticsPeriod(Instant from, Instant to) {
    public boolean isEmpty() {
        return from == null && to == null;
    }

    public String label() {
        if (from == null && to == null) {
            return "N/A";
        }
        return (from == null ? "N/A" : from) + " to " + (to == null ? "N/A" : to);
    }
}
