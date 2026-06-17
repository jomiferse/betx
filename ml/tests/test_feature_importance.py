import numpy as np
import pandas as pd

from betx_ml.feature_importance import export_feature_importance
from betx_ml.features import add_features
from betx_ml.models import train_model


def frame() -> pd.DataFrame:
    rows = []
    for index in range(18):
        rows.append(
            {
                "market_key": f"k{index}",
                "date": pd.Timestamp("2024-08-01", tz="UTC") + pd.Timedelta(days=index),
                "match_date": (pd.Timestamp("2024-08-01") + pd.Timedelta(days=index)).date(),
                "league": "E0" if index % 2 == 0 else "SP1",
                "season": "2024/25",
                "home_team": f"H{index % 4}",
                "away_team": f"A{index % 4}",
                "home_team_key": f"h{index % 4}",
                "away_team_key": f"a{index % 4}",
                "actual_result": ["HOME", "DRAW", "AWAY"][index % 3],
                "fthg": [2, 1, 0][index % 3],
                "ftag": [0, 1, 2][index % 3],
                "ftr": ["H", "D", "A"][index % 3],
                "home_odds": [1.7, 3.2, 5.0][index % 3],
                "draw_odds": [4.0, 2.8, 4.0][index % 3],
                "away_odds": [5.0, 3.2, 1.7][index % 3],
            }
        )
    return add_features(pd.DataFrame(rows), feature_set="odds_elo")


def test_logistic_importance_exports_transformed_feature_names_by_class():
    data = frame()
    model = train_model(data, model_name="logistic_regression", feature_set="odds_elo", random_seed=42)

    importance = export_feature_importance(model, "logistic_regression", "odds_elo", data, random_seed=42)

    assert {"HOME", "DRAW", "AWAY"} <= set(importance["class"])
    assert "numeric__home_elo_pre" in set(importance["feature"])
    assert importance["importance_std"].eq(0.0).all()


def test_hgb_importance_uses_validation_frame_and_global_class():
    data = frame()
    model = train_model(data, model_name="hist_gradient_boosting", feature_set="odds_elo", random_seed=42)

    importance = export_feature_importance(model, "hist_gradient_boosting", "odds_elo", data, random_seed=42, n_repeats=2)

    assert not importance.empty
    assert set(importance["class"]) == {""}
    assert np.isfinite(importance["importance_mean"]).all()
