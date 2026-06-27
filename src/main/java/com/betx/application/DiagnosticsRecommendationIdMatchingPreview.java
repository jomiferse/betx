package com.betx.application;

/** Preview-only recommendation_id matching metrics. Official matching remains legacy. */
public record DiagnosticsRecommendationIdMatchingPreview(
    boolean previewAvailable,
    boolean enabledAsOfficialMatching,
    DiagnosticsRecommendationIdMatchingScope allTime,
    DiagnosticsRecommendationIdMatchingScope post25
) {
    public DiagnosticsRecommendationIdMatchingPreview {
        allTime = allTime == null ? DiagnosticsRecommendationIdMatchingScope.empty() : allTime;
        post25 = post25 == null ? DiagnosticsRecommendationIdMatchingScope.empty() : post25;
    }

    public static DiagnosticsRecommendationIdMatchingPreview empty() {
        return new DiagnosticsRecommendationIdMatchingPreview(
            false,
            false,
            DiagnosticsRecommendationIdMatchingScope.empty(),
            DiagnosticsRecommendationIdMatchingScope.empty()
        );
    }
}
