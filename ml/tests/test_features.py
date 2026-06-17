import pandas as pd
import pytest

from betx_ml.features import add_market_features, chronological_split


def market_frame() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "market_key": "k1",
                "date": "2024-08-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "home_odds": 2.0,
                "draw_odds": 4.0,
                "away_odds": 4.0,
                "actual_result": "HOME",
            },
            {
                "market_key": "k2",
                "date": "2024-09-01T15:00:00Z",
                "league": "SP1",
                "season": "2024/25",
                "home_odds": 3.0,
                "draw_odds": 3.0,
                "away_odds": 3.0,
                "actual_result": "DRAW",
            },
            {
                "market_key": "k3",
                "date": "2024-10-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "home_odds": 4.0,
                "draw_odds": 4.0,
                "away_odds": 2.0,
                "actual_result": "AWAY",
            },
        ]
    )


def test_adds_normalized_market_probabilities_and_overround():
    features = add_market_features(market_frame())
    first = features.iloc[0]

    assert first["home_implied_probability"] == pytest.approx(0.5)
    assert first["draw_implied_probability"] == pytest.approx(0.25)
    assert first["away_implied_probability"] == pytest.approx(0.25)
    assert first["overround"] == pytest.approx(1.0)
    assert first["market_home_probability"] + first["market_draw_probability"] + first["market_away_probability"] == pytest.approx(1.0)
    assert first["market_home_probability"] == pytest.approx(0.5)


def test_chronological_split_uses_dates_without_shuffle():
    features = add_market_features(market_frame())

    split = chronological_split(features, validation_size=1 / 3, test_size=1 / 3)

    assert split.train["market_key"].tolist() == ["k1"]
    assert split.validation["market_key"].tolist() == ["k2"]
    assert split.test["market_key"].tolist() == ["k3"]


def test_chronological_split_rejects_empty_partitions():
    with pytest.raises(ValueError, match="at least one row"):
        chronological_split(add_market_features(market_frame().head(2)), validation_size=0.5, test_size=0.5)

