import numpy as np
import pandas as pd
import pytest

from betx_ml.evaluation import brier_score_multiclass, calibration_table, evaluate_probabilities, walk_forward_splits


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

