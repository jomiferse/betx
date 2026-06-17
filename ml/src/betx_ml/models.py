from __future__ import annotations

from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from betx_ml.features import CATEGORICAL_FEATURES, LABELS, feature_columns


MODEL_NAMES = ["logistic_regression", "hist_gradient_boosting"]


def train_logistic_regression(frame: pd.DataFrame, random_seed: int = 42) -> Pipeline:
    return train_model(frame, model_name="logistic_regression", feature_set="odds_only", random_seed=random_seed)


def train_model(
    frame: pd.DataFrame,
    model_name: str = "logistic_regression",
    feature_set: str = "odds_only",
    random_seed: int = 42,
) -> Pipeline:
    columns = feature_columns(feature_set)
    numeric = [column for column in columns if column not in CATEGORICAL_FEATURES]
    categorical = [column for column in CATEGORICAL_FEATURES if column in columns]
    if model_name == "logistic_regression":
        model = LogisticRegression(max_iter=1000, random_state=random_seed)
        preprocessor = ColumnTransformer(
            [
                ("numeric", Pipeline([("imputer", SimpleImputer()), ("scaler", StandardScaler())]), numeric),
                ("categorical", OneHotEncoder(handle_unknown="ignore", sparse_output=False), categorical),
            ],
            sparse_threshold=0.0,
        )
    elif model_name == "hist_gradient_boosting":
        model = HistGradientBoostingClassifier(random_state=random_seed, max_iter=100, learning_rate=0.05)
        preprocessor = ColumnTransformer(
            [
                ("numeric", Pipeline([("imputer", SimpleImputer())]), numeric),
                ("categorical", OneHotEncoder(handle_unknown="ignore", sparse_output=False), categorical),
            ],
            sparse_threshold=0.0,
        )
    else:
        raise ValueError(f"Unsupported model: {model_name}")
    pipeline = Pipeline([("preprocessor", preprocessor), ("model", model)])
    pipeline.fit(frame[columns], frame["actual_result"])
    return pipeline


def predict_probabilities(model: Pipeline, frame: pd.DataFrame, feature_set: str = "odds_only") -> np.ndarray:
    raw = model.predict_proba(frame[feature_columns(feature_set)])
    classes = list(model.named_steps["model"].classes_)
    ordered = np.zeros((len(frame), len(LABELS)))
    for source_index, label in enumerate(classes):
        ordered[:, LABELS.index(label)] = raw[:, source_index]
    return ordered


def save_model(model: Pipeline, path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, path)


def load_model(path: str | Path) -> Pipeline:
    return joblib.load(path)
