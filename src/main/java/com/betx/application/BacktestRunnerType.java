package com.betx.application;

/** Runner role inferred from football-data match odds selections. */
public enum BacktestRunnerType {
    HOME,
    DRAW,
    AWAY,
    UNKNOWN;

    public static BacktestRunnerType fromSelectionId(long selectionId) {
        return switch ((int) selectionId) {
            case 1 -> HOME;
            case 2 -> DRAW;
            case 3 -> AWAY;
            default -> UNKNOWN;
        };
    }
}
