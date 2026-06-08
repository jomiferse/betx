package com.betx.application;

public record MatchIntelligenceSource(String title, String url, String date) {
    public MatchIntelligenceSource {
        title = blankToNull(title);
        url = blankToNull(url);
        date = blankToNull(date);
    }

    public static MatchIntelligenceSource fromUrl(String url) {
        return new MatchIntelligenceSource(null, url, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
