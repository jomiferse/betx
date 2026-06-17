from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.inspection import permutation_importance
from sklearn.pipeline import Pipeline

from betx_ml.features import LABELS, feature_columns


def export_feature_importance(
    model: Pipeline,
    model_name: str,
    feature_set: str,
    validation_frame: pd.DataFrame,
    random_seed: int = 42,
    n_repeats: int = 5,
) -> pd.DataFrame:
    if model_name == "logistic_regression":
        return _logistic_coefficients(model, model_name, feature_set)
    if model_name == "hist_gradient_boosting":
        return _hgb_permutation_importance(model, model_name, feature_set, validation_frame, random_seed, n_repeats)
    raise ValueError(f"Unsupported model for feature importance: {model_name}")


def _logistic_coefficients(model: Pipeline, model_name: str, feature_set: str) -> pd.DataFrame:
    preprocessor = model.named_steps["preprocessor"]
    classifier = model.named_steps["model"]
    names = preprocessor.get_feature_names_out()
    rows: list[dict[str, object]] = []
    for class_index, label in enumerate(classifier.classes_):
        ordered_label = label if label in LABELS else str(label)
        for feature, coefficient in zip(names, classifier.coef_[class_index], strict=True):
            rows.append(
                {
                    "model": model_name,
                    "feature_set": feature_set,
                    "feature": feature,
                    "importance_mean": float(coefficient),
                    "importance_std": 0.0,
                    "class": ordered_label,
                }
            )
    return pd.DataFrame(rows)


def _hgb_permutation_importance(
    model: Pipeline,
    model_name: str,
    feature_set: str,
    validation_frame: pd.DataFrame,
    random_seed: int,
    n_repeats: int,
) -> pd.DataFrame:
    columns = feature_columns(feature_set)
    result = permutation_importance(
        model,
        validation_frame[columns],
        validation_frame["actual_result"],
        n_repeats=n_repeats,
        random_state=random_seed,
        scoring="neg_log_loss",
    )
    rows = [
        {
            "model": model_name,
            "feature_set": feature_set,
            "feature": column,
            "importance_mean": float(mean),
            "importance_std": float(std),
            "class": "",
        }
        for column, mean, std in zip(columns, result.importances_mean, result.importances_std, strict=True)
    ]
    return pd.DataFrame(rows)
