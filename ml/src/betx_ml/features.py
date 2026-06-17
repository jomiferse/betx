from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from betx_ml.team_history import TEAM_STRENGTH_FEATURES, add_team_strength_features


LABELS = ["HOME", "DRAW", "AWAY"]
NUMERIC_FEATURES = [
    "home_odds",
    "draw_odds",
    "away_odds",
    "home_implied_probability",
    "draw_implied_probability",
    "away_implied_probability",
    "market_home_probability",
    "market_draw_probability",
    "market_away_probability",
    "overround",
    "month",
    "season_month_index",
]
CATEGORICAL_FEATURES = ["league"]
FEATURE_COLUMNS = NUMERIC_FEATURES + CATEGORICAL_FEATURES
ELO_FEATURES = ["home_elo_pre", "away_elo_pre", "elo_diff"]
FORM_FEATURES = [
    "home_points_last_5",
    "away_points_last_5",
    "home_draw_rate_last_10",
    "away_draw_rate_last_10",
    "home_matches_available",
    "away_matches_available",
]
GOAL_FEATURES = ["home_goal_diff_last_5", "away_goal_diff_last_5"]
TEAM_STRENGTH_ALL_FEATURES = TEAM_STRENGTH_FEATURES
FEATURE_SETS = {
    "odds_only": FEATURE_COLUMNS,
    "odds_elo": NUMERIC_FEATURES + ELO_FEATURES + CATEGORICAL_FEATURES,
    "odds_form": NUMERIC_FEATURES + FORM_FEATURES + CATEGORICAL_FEATURES,
    "odds_goals": NUMERIC_FEATURES + GOAL_FEATURES + CATEGORICAL_FEATURES,
    "odds_elo_form": NUMERIC_FEATURES + ELO_FEATURES + FORM_FEATURES + CATEGORICAL_FEATURES,
    "odds_team_strength_all": NUMERIC_FEATURES + TEAM_STRENGTH_ALL_FEATURES + CATEGORICAL_FEATURES,
}
LEAKAGE_FEATURE_PATTERNS = [
    "closing",
    "actual_result",
    "fthg",
    "ftag",
    "ftr",
    "pnl",
    "clv",
    "settlement",
    "won",
]


@dataclass(frozen=True)
class TemporalSplit:
    train: pd.DataFrame
    validation: pd.DataFrame
    test: pd.DataFrame


def add_market_features(markets: pd.DataFrame) -> pd.DataFrame:
    frame = markets.copy()
    frame["date"] = pd.to_datetime(frame["date"], utc=True)
    frame = frame.sort_values("date").reset_index(drop=True)
    for selection in ("home", "draw", "away"):
        frame[f"{selection}_implied_probability"] = 1.0 / frame[f"{selection}_odds"].astype(float)
    frame["overround"] = (
        frame["home_implied_probability"]
        + frame["draw_implied_probability"]
        + frame["away_implied_probability"]
    )
    for selection in ("home", "draw", "away"):
        frame[f"market_{selection}_probability"] = frame[f"{selection}_implied_probability"] / frame["overround"]
    frame["month"] = frame["date"].dt.month
    frame["season_month_index"] = ((frame["month"] - 7) % 12) + 1
    return frame


def add_features(markets: pd.DataFrame, feature_set: str = "odds_only") -> pd.DataFrame:
    featured = add_market_features(markets)
    if feature_set != "odds_only":
        featured = add_team_strength_features(featured)
    feature_columns(feature_set)
    return featured


def feature_columns(feature_set: str = "odds_only") -> list[str]:
    try:
        return FEATURE_SETS[feature_set]
    except KeyError as exc:
        raise ValueError(f"Unsupported feature set: {feature_set}") from exc


def validate_feature_sets() -> None:
    for name, columns in FEATURE_SETS.items():
        seen: set[str] = set()
        duplicates = [column for column in columns if column in seen or seen.add(column)]
        if duplicates:
            raise ValueError(f"Duplicate features in {name}: {', '.join(duplicates)}")
        for column in columns:
            lowered = column.lower()
            if any(pattern in lowered for pattern in LEAKAGE_FEATURE_PATTERNS):
                raise ValueError(f"Feature set {name} contains leakage column: {column}")


def feature_frame(markets: pd.DataFrame, feature_set: str = "odds_only") -> tuple[pd.DataFrame, pd.Series]:
    featured = add_features(markets, feature_set=feature_set)
    return featured[feature_columns(feature_set)], featured["actual_result"]


def chronological_split(markets: pd.DataFrame, validation_size: float = 0.15, test_size: float = 0.20) -> TemporalSplit:
    if not 0 < validation_size < 1 or not 0 < test_size < 1 or validation_size + test_size > 1:
        raise ValueError("validation_size and test_size must be positive fractions whose sum is at most 1")
    frame = markets.copy()
    frame["date"] = pd.to_datetime(frame["date"], utc=True)
    frame = frame.sort_values("date").reset_index(drop=True)
    n_rows = len(frame)
    validation_rows = max(1, int(np.ceil(n_rows * validation_size)))
    test_rows = max(1, int(np.ceil(n_rows * test_size)))
    train_rows = n_rows - validation_rows - test_rows
    if train_rows < 1:
        raise ValueError("Each chronological split must contain at least one row")
    train_rows = _advance_same_timestamp_boundary(frame, train_rows)
    validation_end = _advance_same_timestamp_boundary(frame, train_rows + validation_rows)
    if validation_end >= n_rows:
        raise ValueError("Each chronological split must contain at least one row")
    return TemporalSplit(
        train=frame.iloc[:train_rows].reset_index(drop=True),
        validation=frame.iloc[train_rows:validation_end].reset_index(drop=True),
        test=frame.iloc[validation_end:].reset_index(drop=True),
    )


def _advance_same_timestamp_boundary(frame: pd.DataFrame, boundary: int) -> int:
    if boundary <= 0 or boundary >= len(frame):
        return boundary
    while boundary < len(frame) and frame.loc[boundary - 1, "date"] == frame.loc[boundary, "date"]:
        boundary += 1
    return boundary
