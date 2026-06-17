from __future__ import annotations

from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from betx_ml.features import CATEGORICAL_FEATURES, FEATURE_COLUMNS, LABELS, NUMERIC_FEATURES


def train_logistic_regression(frame: pd.DataFrame, random_seed: int = 42) -> Pipeline:
    preprocessor = ColumnTransformer(
        [
            ("numeric", StandardScaler(), NUMERIC_FEATURES),
            ("categorical", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
        ]
    )
    model = LogisticRegression(
        max_iter=1000,
        random_state=random_seed,
    )
    pipeline = Pipeline([("preprocessor", preprocessor), ("model", model)])
    pipeline.fit(frame[FEATURE_COLUMNS], frame["actual_result"])
    return pipeline


def predict_probabilities(model: Pipeline, frame: pd.DataFrame) -> np.ndarray:
    raw = model.predict_proba(frame[FEATURE_COLUMNS])
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
