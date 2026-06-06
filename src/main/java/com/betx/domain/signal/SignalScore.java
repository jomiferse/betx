package com.betx.domain.signal;

import java.util.List;

/** Explainable 0-100 confidence score for one runner signal. */
public record SignalScore(int value, String confidenceLabel, List<String> reasons) {
    public SignalScore {
        value = Math.max(0, Math.min(100, value));
        confidenceLabel = confidenceLabel == null || confidenceLabel.isBlank() ? label(value) : confidenceLabel;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static SignalScore zero(String reason) {
        return new SignalScore(0, "Low confidence", reason == null || reason.isBlank() ? List.of() : List.of(reason));
    }

    public static SignalScore fromValue(int value, List<String> reasons) {
        return new SignalScore(value, label(value), reasons);
    }

    private static String label(int value) {
        if (value >= 70) {
            return "High confidence";
        }
        if (value >= 40) {
            return "Medium confidence";
        }
        return "Low confidence";
    }
}
