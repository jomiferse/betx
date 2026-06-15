package com.betx.adapter.backtest;

/** Supported Football-Data odds columns for a single normalized research run. */
public enum FootballDataOddsSource {
    OPENING_BOOKMAKER("opening-bookmaker", "B365H", "B365D", "B365A", 12),
    CLOSING_AVERAGE("closing-average", "B365CH", "B365CD", "B365CA", 2);

    private final String id;
    private final String homeColumn;
    private final String drawColumn;
    private final String awayColumn;
    private final int observedHoursBeforeKickoff;

    FootballDataOddsSource(
        String id,
        String homeColumn,
        String drawColumn,
        String awayColumn,
        int observedHoursBeforeKickoff
    ) {
        this.id = id;
        this.homeColumn = homeColumn;
        this.drawColumn = drawColumn;
        this.awayColumn = awayColumn;
        this.observedHoursBeforeKickoff = observedHoursBeforeKickoff;
    }

    public String id() {
        return id;
    }

    String homeColumn() {
        return homeColumn;
    }

    String drawColumn() {
        return drawColumn;
    }

    String awayColumn() {
        return awayColumn;
    }

    int observedHoursBeforeKickoff() {
        return observedHoursBeforeKickoff;
    }

    public static FootballDataOddsSource fromId(String value) {
        if (value == null || value.isBlank()) {
            return CLOSING_AVERAGE;
        }
        for (FootballDataOddsSource source : values()) {
            if (source.id.equalsIgnoreCase(value.strip()) || source.name().equalsIgnoreCase(value.strip())) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unsupported odds source: " + value);
    }
}
