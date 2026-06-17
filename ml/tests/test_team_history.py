import pandas as pd
import pytest

from betx_ml.team_history import TEAM_STRENGTH_FEATURES, add_team_strength_features


def match(
    key: str,
    date: str,
    home: str,
    away: str,
    fthg: int,
    ftag: int,
    *,
    season: str = "2024/25",
    league: str = "E0",
) -> dict[str, object]:
    result = "HOME" if fthg > ftag else "AWAY" if ftag > fthg else "DRAW"
    return {
        "market_key": key,
        "date": pd.Timestamp(date, tz="UTC"),
        "match_date": pd.Timestamp(date).date(),
        "league": league,
        "season": season,
        "home_team": home,
        "away_team": away,
        "home_team_key": home.lower(),
        "away_team_key": away.lower(),
        "fthg": fthg,
        "ftag": ftag,
        "ftr": {"HOME": "H", "AWAY": "A", "DRAW": "D"}[result],
        "actual_result": result,
        "home_odds": 2.0,
        "draw_odds": 3.4,
        "away_odds": 3.8,
    }


def test_team_strength_features_use_defaults_before_first_match():
    frame = pd.DataFrame([match("m1", "2024-08-10", "Alpha", "Beta", 2, 1)])

    featured = add_team_strength_features(frame)

    first = featured.iloc[0]
    assert set(TEAM_STRENGTH_FEATURES).issubset(featured.columns)
    assert first["home_elo_pre"] == pytest.approx(1500.0)
    assert first["away_elo_pre"] == pytest.approx(1500.0)
    assert first["elo_diff"] == pytest.approx(0.0)
    assert first["home_matches_available"] == 0
    assert first["away_matches_available"] == 0
    assert first["home_days_since_last_match"] == 14
    assert first["away_days_since_last_match"] == 14
    assert first["home_draw_rate_last_10"] == pytest.approx(0.0)
    assert first["away_draw_rate_last_10"] == pytest.approx(0.0)


def test_features_use_only_prior_dates_not_same_day_results():
    frame = pd.DataFrame(
        [
            match("m1", "2024-08-10", "Alpha", "Beta", 2, 1),
            match("m2", "2024-08-10", "Gamma", "Alpha", 0, 3),
            match("m3", "2024-08-17", "Alpha", "Gamma", 1, 1),
        ]
    )

    featured = add_team_strength_features(frame)

    same_day_alpha = featured[featured["market_key"] == "m2"].iloc[0]
    later_alpha = featured[featured["market_key"] == "m3"].iloc[0]
    assert same_day_alpha["away_matches_available"] == 0
    assert same_day_alpha["away_points_last_5"] == 0
    assert later_alpha["home_matches_available"] == 2
    assert later_alpha["home_points_last_5"] == 6
    assert later_alpha["home_draw_rate_last_10"] == pytest.approx(0.0)


def test_future_result_changes_do_not_change_past_features():
    frame = pd.DataFrame(
        [
            match("m1", "2024-08-10", "Alpha", "Beta", 2, 1),
            match("m2", "2024-08-17", "Alpha", "Gamma", 1, 1),
        ]
    )
    changed = frame.copy()
    changed.loc[1, "fthg"] = 0
    changed.loc[1, "ftag"] = 4
    changed.loc[1, "actual_result"] = "AWAY"
    changed.loc[1, "ftr"] = "A"

    original = add_team_strength_features(frame)
    mutated = add_team_strength_features(changed)

    assert original.loc[0, TEAM_STRENGTH_FEATURES].to_dict() == mutated.loc[0, TEAM_STRENGTH_FEATURES].to_dict()


def test_inactivity_decay_moves_elo_toward_baseline():
    frame = pd.DataFrame(
        [
            match("m1", "2024-08-10", "Alpha", "Beta", 5, 0),
            match("m2", "2024-10-15", "Alpha", "Gamma", 1, 1),
        ]
    )

    featured = add_team_strength_features(frame)

    second = featured[featured["market_key"] == "m2"].iloc[0]
    assert second["home_elo_pre"] > 1500
    assert second["home_inactivity_decay_applied"] == 1
