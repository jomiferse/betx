from __future__ import annotations

import argparse
import hashlib
import sys
from datetime import UTC, datetime
from pathlib import Path

import numpy as np
import pandas as pd

from betx_ml.betting import select_value_bets, settle_bets, summarize_bets
from betx_ml.dataset import load_markets
from betx_ml.evaluation import evaluate_probabilities, walk_forward_splits
from betx_ml.export import create_run_dir, write_csv, write_json, write_predictions
from betx_ml.features import FEATURE_COLUMNS, LABELS, add_market_features, chronological_split
from betx_ml.models import load_model, predict_probabilities, save_model, train_logistic_regression


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "train":
            train(args)
        elif args.command == "predict":
            predict(args)
        else:
            parser.print_help()
            return 1
    except Exception as exc:  # noqa: BLE001 - CLI boundary should be readable.
        print(f"Error: {exc}", file=sys.stderr)
        return 2
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="betx-ml", description="Offline BetX football probability baseline.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    train_parser = subparsers.add_parser("train", help="Train and evaluate the offline probability baseline.")
    train_parser.add_argument("--input", required=True, type=Path, help="BetX normalized runner-level history CSV.")
    train_parser.add_argument("--output-dir", required=True, type=Path, help="Directory where run folders are created.")
    train_parser.add_argument("--test-size", type=float, default=0.20)
    train_parser.add_argument("--validation-size", type=float, default=0.15)
    train_parser.add_argument("--random-seed", type=int, default=42)
    train_parser.add_argument("--min-edge", type=float, default=0.03)
    train_parser.add_argument("--stake", type=float, default=5.0)
    train_parser.add_argument("--commission-rate", type=float, default=0.05)
    train_parser.add_argument("--slippage-rate", type=float, default=0.02)
    train_parser.add_argument("--run-id", default=None)

    predict_parser = subparsers.add_parser("predict", help="Generate predictions with a saved model.")
    predict_parser.add_argument("--model", required=True, type=Path, help="Path to model.joblib.")
    predict_parser.add_argument("--input", required=True, type=Path, help="BetX normalized runner-level history CSV.")
    predict_parser.add_argument("--output", required=True, type=Path, help="Prediction CSV output path.")

    return parser


def train(args: argparse.Namespace) -> None:
    dataset = load_markets(args.input)
    markets = add_market_features(dataset.markets)
    split = chronological_split(markets, validation_size=args.validation_size, test_size=args.test_size)
    run_dir = create_run_dir(args.output_dir, args.run_id)

    model = train_logistic_regression(split.train, random_seed=args.random_seed)
    save_model(model, run_dir / "model.joblib")

    predictions = pd.concat(
        [
            _prediction_frame(model, split.train, "train"),
            _prediction_frame(model, split.validation, "validation"),
            _prediction_frame(model, split.test, "test"),
        ],
        ignore_index=True,
    )
    out_of_sample_predictions = predictions[predictions["split"] == "test"].reset_index(drop=True)
    bets = settle_bets(
        select_value_bets(out_of_sample_predictions, min_edge=args.min_edge, stake=args.stake, slippage_rate=args.slippage_rate),
        commission_rate=args.commission_rate,
    )

    market_test_probabilities = split.test[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
    model_test_probabilities = predictions[predictions["split"] == "test"][
        ["model_home_probability", "model_draw_probability", "model_away_probability"]
    ].to_numpy()
    market_metrics = evaluate_probabilities(split.test["actual_result"], market_test_probabilities)
    model_metrics = evaluate_probabilities(split.test["actual_result"], model_test_probabilities)
    walk_forward = _walk_forward_results(markets, args.random_seed, args.test_size)
    result_label = _result_label(model_metrics, market_metrics, walk_forward)

    metrics = {
        "result": result_label,
        "dataset": {
            "rows": int(len(markets)),
            "train": int(len(split.train)),
            "validation": int(len(split.validation)),
            "test": int(len(split.test)),
        },
        "market_baseline": market_metrics,
        "logistic_regression": model_metrics,
        "value_betting": summarize_bets(bets),
        "walk_forward": walk_forward.to_dict(orient="records"),
    }

    write_predictions(run_dir / "predictions.csv", predictions)
    write_csv(run_dir / "bets.csv", bets)
    write_csv(run_dir / "walk_forward_results.csv", walk_forward)
    write_json(run_dir / "metrics.json", metrics)
    write_json(run_dir / "dataset_quality.json", dataset.quality.to_dict())
    write_json(
        run_dir / "feature_manifest.json",
        {
            "format_version": 1,
            "features": FEATURE_COLUMNS,
            "target": "actual_result",
            "predictive_odds_source": "opening-bookmaker",
            "closing_odds_usage": "CLV only",
            "leakage_controls": [
                "Only opening-bookmaker odds are used as model input.",
                "Closing odds are joined after prediction only for CLV metrics.",
                "Splits are chronological by market_start_time with no shuffle.",
            ],
        },
    )
    write_json(
        run_dir / "model_metadata.json",
        {
            "algorithm": "LogisticRegression",
            "features": FEATURE_COLUMNS,
            "trained_at": datetime.now(UTC).isoformat(),
            "training_period": _period(split.train),
            "validation_period": _period(split.validation),
            "test_period": _period(split.test),
            "rows": {"train": len(split.train), "validation": len(split.validation), "test": len(split.test)},
            "random_seed": args.random_seed,
            "parameters": {"max_iter": 1000},
            "format_version": 1,
            "dataset_hash": _sha256(args.input),
        },
    )

    print(_summary_text(metrics, run_dir))


def predict(args: argparse.Namespace) -> None:
    dataset = load_markets(args.input)
    markets = add_market_features(dataset.markets)
    model = load_model(args.model)
    predictions = _prediction_frame(model, markets, "prediction")
    write_predictions(args.output, predictions)
    print(f"Wrote {len(predictions)} predictions to {args.output}")


def _prediction_frame(model: object, frame: pd.DataFrame, split: str) -> pd.DataFrame:
    probabilities = predict_probabilities(model, frame)
    result = frame.copy()
    result["model_home_probability"] = probabilities[:, 0]
    result["model_draw_probability"] = probabilities[:, 1]
    result["model_away_probability"] = probabilities[:, 2]
    result["predicted_result"] = [LABELS[index] for index in np.argmax(probabilities, axis=1)]
    result["split"] = split
    return result


def _walk_forward_results(markets: pd.DataFrame, random_seed: int, test_fraction: float, max_folds: int = 3) -> pd.DataFrame:
    target_fold_size = int(len(markets) * test_fraction)
    fold_size = max(1, min(target_fold_size, max(1, len(markets) // (max_folds + 8))))
    min_train = max(1, len(markets) - (fold_size * (max_folds + 1)))
    rows: list[dict[str, object]] = []
    for index, (train_frame, test_frame) in enumerate(
        walk_forward_splits(markets, min_train_size=min_train, test_size=fold_size, max_folds=max_folds),
        start=1,
    ):
        model = train_logistic_regression(train_frame, random_seed=random_seed)
        model_probabilities = predict_probabilities(model, test_frame)
        market_probabilities = test_frame[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
        model_metrics = evaluate_probabilities(test_frame["actual_result"], model_probabilities)
        market_metrics = evaluate_probabilities(test_frame["actual_result"], market_probabilities)
        rows.append(
            {
                "fold": index,
                "train_start": train_frame["date"].min().isoformat(),
                "train_end": train_frame["date"].max().isoformat(),
                "train_rows": len(train_frame),
                "test_start": test_frame["date"].min().isoformat(),
                "test_end": test_frame["date"].max().isoformat(),
                "test_rows": len(test_frame),
                "market_log_loss": market_metrics["log_loss"],
                "model_log_loss": model_metrics["log_loss"],
                "market_brier_score": market_metrics["brier_score"],
                "model_brier_score": model_metrics["brier_score"],
                "model_beats_market": bool(
                    model_metrics["log_loss"] < market_metrics["log_loss"]
                    and model_metrics["brier_score"] < market_metrics["brier_score"]
                ),
            }
        )
    return pd.DataFrame(rows)


def _result_label(model_metrics: dict[str, object], market_metrics: dict[str, object], walk_forward: pd.DataFrame) -> str:
    test_beats = (
        float(model_metrics["log_loss"]) < float(market_metrics["log_loss"])
        and float(model_metrics["brier_score"]) < float(market_metrics["brier_score"])
    )
    fold_beats = bool(not walk_forward.empty and walk_forward["model_beats_market"].all())
    return "MODEL_BEATS_MARKET_BASELINE" if test_beats and fold_beats else "MODEL_DOES_NOT_BEAT_MARKET_BASELINE"


def _summary_text(metrics: dict[str, object], run_dir: Path) -> str:
    dataset = metrics["dataset"]
    market = metrics["market_baseline"]
    model = metrics["logistic_regression"]
    bets = metrics["value_betting"]
    return "\n".join(
        [
            "Dataset",
            f"Rows: {dataset['rows']}",
            f"Train: {dataset['train']}",
            f"Validation: {dataset['validation']}",
            f"Test: {dataset['test']}",
            "",
            "Market baseline",
            f"Log loss: {market['log_loss']:.6f}",
            f"Brier score: {market['brier_score']:.6f}",
            "",
            "Logistic regression",
            f"Log loss: {model['log_loss']:.6f}",
            f"Brier score: {model['brier_score']:.6f}",
            "",
            "Value betting test",
            f"Trades: {bets['trades']}",
            f"Net ROI: {bets['net_roi']:.4f}",
            f"Max drawdown: {bets['max_drawdown']:.4f}",
            f"Median CLV: {bets['median_clv'] if bets['median_clv'] is not None else 'NOT_AVAILABLE'}",
            "",
            f"Result: {metrics['result']}",
            f"Run directory: {run_dir}",
        ]
    )


def _period(frame: pd.DataFrame) -> dict[str, str]:
    return {"start": frame["date"].min().isoformat(), "end": frame["date"].max().isoformat()}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
