from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, confusion_matrix, precision_recall_fscore_support

from betx_ml.features import LABELS


def brier_score_multiclass(y_true: list[str] | pd.Series, probabilities: np.ndarray) -> float:
    encoded = _one_hot(y_true)
    return float(np.mean(np.sum((probabilities - encoded) ** 2, axis=1)))


def evaluate_probabilities(y_true: list[str] | pd.Series, probabilities: np.ndarray) -> dict[str, object]:
    y = list(y_true)
    predicted = [LABELS[index] for index in np.argmax(probabilities, axis=1)]
    precision, recall, f1, support = precision_recall_fscore_support(
        y,
        predicted,
        labels=LABELS,
        zero_division=0,
    )
    return {
        "count": len(y),
        "log_loss": log_loss_multiclass(y, probabilities),
        "brier_score": brier_score_multiclass(y, probabilities),
        "accuracy": float(accuracy_score(y, predicted)),
        "confusion_matrix": confusion_matrix(y, predicted, labels=LABELS).tolist(),
        "per_class": {
            label: {
                "precision": float(precision[index]),
                "recall": float(recall[index]),
                "f1": float(f1[index]),
                "support": int(support[index]),
            }
            for index, label in enumerate(LABELS)
        },
        "calibration": calibration_table(y, probabilities).to_dict(orient="records"),
    }


def calibration_table(y_true: list[str] | pd.Series, probabilities: np.ndarray, bins: int = 10) -> pd.DataFrame:
    rows: list[dict[str, object]] = []
    y = list(y_true)
    for selection_index, label in enumerate(LABELS):
        for bucket_index in range(bins):
            low = bucket_index / bins
            high = (bucket_index + 1) / bins
            if bucket_index == bins - 1:
                mask = (probabilities[:, selection_index] >= low) & (probabilities[:, selection_index] <= high)
            else:
                mask = (probabilities[:, selection_index] >= low) & (probabilities[:, selection_index] < high)
            count = int(mask.sum())
            observed = [1 if actual == label else 0 for actual, include in zip(y, mask, strict=True) if include]
            rows.append(
                {
                    "selection": label,
                    "bucket": f"{low:.1f}-{high:.1f}",
                    "predictions": count,
                    "average_probability": float(probabilities[mask, selection_index].mean()) if count else None,
                    "actual_rate": float(np.mean(observed)) if count else None,
                }
            )
    return pd.DataFrame(rows)


def log_loss_multiclass(y_true: list[str] | pd.Series, probabilities: np.ndarray) -> float:
    mapping = {label: index for index, label in enumerate(LABELS)}
    clipped = np.clip(probabilities, 1e-15, 1.0)
    losses = [-np.log(clipped[row_index, mapping[label]]) for row_index, label in enumerate(y_true)]
    return float(np.mean(losses))


def walk_forward_splits(
    frame: pd.DataFrame,
    min_train_size: int,
    test_size: int,
    max_folds: int | None = None,
) -> list[tuple[pd.DataFrame, pd.DataFrame]]:
    ordered = frame.sort_values("date").reset_index(drop=True)
    folds: list[tuple[pd.DataFrame, pd.DataFrame]] = []
    train_end = min_train_size
    while train_end + test_size <= len(ordered):
        train_end = _advance_same_timestamp_boundary(ordered, train_end)
        test_end = _advance_same_timestamp_boundary(ordered, train_end + test_size)
        if test_end > len(ordered):
            break
        folds.append(
            (
                ordered.iloc[:train_end].reset_index(drop=True),
                ordered.iloc[train_end:test_end].reset_index(drop=True),
            )
        )
        if max_folds is not None and len(folds) >= max_folds:
            break
        train_end = test_end
    return folds


def edge_sensitivity(
    predictions: pd.DataFrame,
    thresholds: list[float],
    *,
    stake: float,
    slippage_rate: float,
    commission_rate: float,
) -> pd.DataFrame:
    from betx_ml.betting import select_value_bets, settle_bets, summarize_bets

    rows: list[dict[str, object]] = []
    for threshold in thresholds:
        bets = settle_bets(
            select_value_bets(predictions, min_edge=threshold, stake=stake, slippage_rate=slippage_rate),
            commission_rate=commission_rate,
        )
        summary = summarize_bets(bets)
        rows.append(
            {
                "threshold": threshold,
                "trades": summary["trades"],
                "net_pnl": summary["net_pnl"],
                "net_roi": summary["net_roi"],
                "max_drawdown": summary["max_drawdown"],
                "median_back_clv_ratio": summary["median_back_clv_ratio"],
                "positive_back_clv_rate": summary["positive_back_clv_rate"],
            }
        )
    return pd.DataFrame(rows)


def select_edge_threshold(validation_sensitivity: pd.DataFrame) -> float:
    if validation_sensitivity.empty:
        raise ValueError("Validation edge sensitivity is empty")
    ordered = validation_sensitivity.sort_values(["net_roi", "trades", "threshold"], ascending=[False, False, True])
    return float(ordered.iloc[0]["threshold"])


def _advance_same_timestamp_boundary(frame: pd.DataFrame, boundary: int) -> int:
    if boundary <= 0 or boundary >= len(frame):
        return boundary
    while boundary < len(frame) and frame.loc[boundary - 1, "date"] == frame.loc[boundary, "date"]:
        boundary += 1
    return boundary


def _one_hot(y_true: list[str] | pd.Series) -> np.ndarray:
    mapping = {label: index for index, label in enumerate(LABELS)}
    encoded = np.zeros((len(y_true), len(LABELS)))
    for row_index, label in enumerate(y_true):
        encoded[row_index, mapping[label]] = 1.0
    return encoded
