import pandas as pd
import pytest

from betx_ml.features import (
    FEATURE_SETS,
    add_features,
    add_market_features,
    chronological_split,
    feature_columns,
    validate_feature_sets,
)


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


def test_odds_team_strength_all_feature_set_adds_only_minimal_team_features():
    frame = market_frame().assign(
        match_date=pd.to_datetime(market_frame()["date"]).dt.date,
        home_team=["Alpha", "Beta", "Alpha"],
        away_team=["Beta", "Gamma", "Gamma"],
        home_team_key=["alpha", "beta", "alpha"],
        away_team_key=["beta", "gamma", "gamma"],
        fthg=[2, 0, 1],
        ftag=[1, 1, 1],
        ftr=["H", "A", "D"],
    )

    featured = add_features(frame, feature_set="odds_team_strength_all")

    columns = feature_columns("odds_team_strength_all")
    assert "home_elo_pre" in columns
    assert "away_inactivity_decay_applied" in columns
    assert "home_home_elo_pre" not in columns
    assert set(columns).issubset(featured.columns)


def test_ablation_feature_sets_are_explicit_and_do_not_leak():
    assert set(FEATURE_SETS) == {
        "odds_only",
        "odds_elo",
        "odds_form",
        "odds_goals",
        "odds_elo_form",
        "odds_team_strength_all",
    }
    assert feature_columns("odds_elo") == feature_columns("odds_only")[:-1] + [
        "home_elo_pre",
        "away_elo_pre",
        "elo_diff",
        "league",
    ]
    form_columns = feature_columns("odds_form")
    assert "home_draw_rate_last_10" in form_columns
    assert "away_draw_rate_last_10" in form_columns
    assert "home_goal_diff_last_5" not in form_columns
    assert "closing_home_odds" not in set().union(*(set(columns) for columns in FEATURE_SETS.values()))
    assert "actual_result" not in set().union(*(set(columns) for columns in FEATURE_SETS.values()))
    assert "fthg" not in set().union(*(set(columns) for columns in FEATURE_SETS.values()))
    validate_feature_sets()


def test_draw_rates_use_only_prior_matches():
    frame = market_frame().assign(
        league=["E0", "E0", "E0"],
        match_date=pd.to_datetime(market_frame()["date"]).dt.date,
        home_team=["Alpha", "Beta", "Alpha"],
        away_team=["Beta", "Alpha", "Gamma"],
        home_team_key=["alpha", "beta", "alpha"],
        away_team_key=["beta", "alpha", "gamma"],
        fthg=[1, 0, 2],
        ftag=[1, 2, 0],
        ftr=["D", "A", "H"],
    )

    featured = add_features(frame, feature_set="odds_form")

    first = featured[featured["market_key"] == "k1"].iloc[0]
    third = featured[featured["market_key"] == "k3"].iloc[0]
    assert first["home_draw_rate_last_10"] == 0.0
    assert first["away_draw_rate_last_10"] == 0.0
    assert third["home_matches_available"] == 2
    assert third["home_draw_rate_last_10"] == 0.5
