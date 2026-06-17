from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd


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


def feature_frame(markets: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    featured = add_market_features(markets)
    return featured[FEATURE_COLUMNS], featured["actual_result"]


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
