from pathlib import Path

import numpy as np
import pandas as pd

from betx_ml.export import create_run_dir, write_json, write_predictions
from betx_ml.features import add_features, add_market_features, chronological_split
from betx_ml.models import load_model, predict_probabilities, save_model, train_logistic_regression, train_model


def training_frame() -> pd.DataFrame:
    rows = []
    for index in range(12):
        if index % 3 == 0:
            odds = (1.7, 4.0, 5.0)
            actual = "HOME"
        elif index % 3 == 1:
            odds = (3.2, 2.8, 3.2)
            actual = "DRAW"
        else:
            odds = (5.0, 4.0, 1.7)
            actual = "AWAY"
        rows.append(
            {
                "market_key": f"k{index}",
                "date": pd.Timestamp("2024-01-01") + pd.Timedelta(days=index),
                "league": "E0" if index % 2 == 0 else "SP1",
                "season": "2024/25",
                "home_team": f"H{index}",
                "away_team": f"A{index}",
                "actual_result": actual,
                "home_odds": odds[0],
                "draw_odds": odds[1],
                "away_odds": odds[2],
            }
        )
    return add_market_features(pd.DataFrame(rows))


def test_logistic_regression_predictions_are_reproducible_and_sum_to_one():
    frame = training_frame()
    split = chronological_split(frame, validation_size=0.25, test_size=0.25)

    model_a = train_logistic_regression(split.train, random_seed=42)
    model_b = train_logistic_regression(split.train, random_seed=42)
    probs_a = predict_probabilities(model_a, split.validation)
    probs_b = predict_probabilities(model_b, split.validation)

    assert np.allclose(probs_a, probs_b)
    assert np.allclose(probs_a.sum(axis=1), 1.0)


def test_model_can_be_saved_loaded_and_reused(tmp_path: Path):
    frame = training_frame()
    model = train_logistic_regression(frame, random_seed=42)
    path = tmp_path / "model.joblib"

    save_model(model, path)
    loaded = load_model(path)

    assert np.allclose(predict_probabilities(model, frame), predict_probabilities(loaded, frame))


def test_hist_gradient_boosting_accepts_dense_team_strength_features():
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
    frame = add_features(pd.DataFrame(rows), feature_set="odds_team_strength_all")

    model = train_model(frame, model_name="hist_gradient_boosting", feature_set="odds_team_strength_all", random_seed=42)
    probabilities = predict_probabilities(model, frame, feature_set="odds_team_strength_all")

    assert probabilities.shape == (len(frame), 3)
    assert np.allclose(probabilities.sum(axis=1), 1.0)


def test_exports_json_and_prediction_csv_contract(tmp_path: Path):
    run_dir = create_run_dir(tmp_path, run_id="test-run")
    predictions = pd.DataFrame(
        [
            {
                "date": "2024-01-01",
                "league": "E0",
                "home_team": "A",
                "away_team": "B",
                "actual_result": "HOME",
                "home_odds": 2.0,
                "draw_odds": 3.2,
                "away_odds": 4.0,
                "market_home_probability": 0.5,
                "market_draw_probability": 0.3,
                "market_away_probability": 0.2,
                "model_home_probability": 0.55,
                "model_draw_probability": 0.25,
                "model_away_probability": 0.20,
                "predicted_result": "HOME",
                "split": "test",
            }
        ]
    )

    write_json(run_dir / "metrics.json", {"result": "MODEL_DOES_NOT_BEAT_MARKET_BASELINE"})
    write_predictions(run_dir / "predictions.csv", predictions)

    assert (run_dir / "metrics.json").read_text().startswith("{")
    exported = pd.read_csv(run_dir / "predictions.csv")
    assert exported.columns.tolist() == [
        "date",
        "league",
        "home_team",
        "away_team",
        "actual_result",
        "home_odds",
        "draw_odds",
        "away_odds",
        "market_home_probability",
        "market_draw_probability",
        "market_away_probability",
        "model_home_probability",
        "model_draw_probability",
        "model_away_probability",
        "predicted_result",
        "split",
    ]
