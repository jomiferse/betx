from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pandas as pd


PREDICTION_COLUMNS = [
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


def create_run_dir(output_dir: str | Path, run_id: str | None = None) -> Path:
    root = Path(output_dir)
    run = run_id or datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    path = root / run
    path.mkdir(parents=True, exist_ok=False)
    return path


def write_json(path: str | Path, payload: dict[str, Any]) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(_json_safe(payload), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_predictions(path: str | Path, predictions: pd.DataFrame) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    predictions[PREDICTION_COLUMNS].to_csv(path, index=False)


def write_csv(path: str | Path, frame: pd.DataFrame) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(path, index=False)


def _json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_safe(item) for item in value]
    if hasattr(value, "item"):
        return value.item()
    if isinstance(value, pd.Timestamp):
        return value.isoformat()
    return value

