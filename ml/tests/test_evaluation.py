import numpy as np
import pandas as pd
import pytest

from betx_ml.evaluation import (
    brier_score_multiclass,
    calibration_table,
    edge_sensitivity,
    evaluate_probabilities,
    select_edge_threshold,
    walk_forward_splits,
)


def test_brier_score_multiclass_matches_manual_calculation():
    y_true = ["HOME", "DRAW"]
    probabilities = np.array([[0.7, 0.2, 0.1], [0.2, 0.5, 0.3]])

    assert brier_score_multiclass(y_true, probabilities) == pytest.approx(((0.3**2 + 0.2**2 + 0.1**2) + (0.2**2 + 0.5**2 + 0.3**2)) / 2)


def test_evaluate_probabilities_returns_log_loss_brier_and_class_metrics():
    y_true = ["HOME", "DRAW", "AWAY"]
    probabilities = np.array([[0.8, 0.1, 0.1], [0.2, 0.6, 0.2], [0.1, 0.3, 0.6]])

    metrics = evaluate_probabilities(y_true, probabilities)

    assert metrics["count"] == 3
    assert metrics["log_loss"] < 0.6
    assert metrics["brier_score"] < 0.3
    assert metrics["accuracy"] == pytest.approx(1.0)
    assert metrics["confusion_matrix"] == [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    assert set(metrics["per_class"]) == {"HOME", "DRAW", "AWAY"}


def test_calibration_table_uses_probability_buckets():
    rows = calibration_table(
        ["HOME", "AWAY"],
        np.array([[0.05, 0.9, 0.05], [0.82, 0.1, 0.08]]),
        bins=10,
    )

    home_bucket = rows[(rows["selection"] == "HOME") & (rows["bucket"] == "0.8-0.9")].iloc[0]
    assert home_bucket["predictions"] == 1
    assert home_bucket["actual_rate"] == 0.0


def test_walk_forward_splits_expand_training_and_keep_future_out():
    frame = pd.DataFrame({"market_key": [f"k{i}" for i in range(8)], "date": pd.date_range("2024-01-01", periods=8)})

    folds = walk_forward_splits(frame, min_train_size=3, test_size=2, max_folds=2)

    assert [(len(train), len(test)) for train, test in folds] == [(3, 2), (5, 2)]
    assert folds[0][0]["market_key"].tolist() == ["k0", "k1", "k2"]
    assert folds[0][1]["market_key"].tolist() == ["k3", "k4"]
    assert folds[1][1]["market_key"].tolist() == ["k5", "k6"]


def test_select_edge_threshold_uses_validation_sensitivity_table():
    sensitivity = pd.DataFrame(
        [
            {"threshold": 0.01, "net_roi": -0.1, "trades": 10},
            {"threshold": 0.03, "net_roi": 0.2, "trades": 4},
            {"threshold": 0.05, "net_roi": 0.2, "trades": 6},
        ]
    )

    assert select_edge_threshold(sensitivity) == pytest.approx(0.05)


def test_edge_sensitivity_reports_only_given_prediction_frame():
    predictions = pd.DataFrame(
        [
            {
                "market_key": "m1",
                "date": "2024-08-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "home_team": "A",
                "away_team": "B",
                "actual_result": "HOME",
                "home_odds": 2.0,
                "draw_odds": 3.4,
                "away_odds": 4.0,
                "closing_home_odds": 1.9,
                "closing_draw_odds": 3.2,
                "closing_away_odds": 4.0,
                "model_home_probability": 0.62,
                "model_draw_probability": 0.25,
                "model_away_probability": 0.13,
            }
        ]
    )

    table = edge_sensitivity(predictions, [0.01, 0.25], stake=5, slippage_rate=0.02, commission_rate=0.05)

    assert table["threshold"].tolist() == [0.01, 0.25]
    assert table.loc[0, "trades"] == 1
    assert table.loc[1, "trades"] == 0
