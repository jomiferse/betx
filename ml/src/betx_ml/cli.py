from __future__ import annotations

import argparse
import hashlib
import sys
from datetime import UTC, datetime
from pathlib import Path

import numpy as np
import pandas as pd

from betx_ml.ablation import run_ablation
from betx_ml.betting import select_value_bets, settle_bets, summarize_bets
from betx_ml.dataset import load_markets
from betx_ml.diagnostics import segment_bets
from betx_ml.evaluation import edge_sensitivity, evaluate_probabilities, select_edge_threshold, walk_forward_splits
from betx_ml.export import create_run_dir, write_csv, write_json, write_predictions
from betx_ml.features import LABELS, add_features, chronological_split, feature_columns
from betx_ml.models import load_model, predict_probabilities, save_model, train_model


MODEL_CONFIGS = [
    {"name": "logistic_regression_odds_only", "model_name": "logistic_regression", "feature_set": "odds_only"},
    {"name": "logistic_regression_team_strength", "model_name": "logistic_regression", "feature_set": "odds_team_strength_all"},
    {"name": "hist_gradient_boosting_team_strength", "model_name": "hist_gradient_boosting", "feature_set": "odds_team_strength_all"},
]
EDGE_THRESHOLDS = [0.01, 0.02, 0.03, 0.05, 0.075, 0.10]


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "train":
            train(args)
        elif args.command == "ablation":
            ablation(args)
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
    train_parser.add_argument("--raw-dir", type=Path, default=None, help="Football-Data raw CSV directory for ML-002 team features.")
    train_parser.add_argument("--diagnostic-only-raw-join", action="store_true", help="Report raw join misses without failing the run.")

    ablation_parser = subparsers.add_parser("ablation", help="Run ML-002.1 feature-family ablation diagnostics.")
    ablation_parser.add_argument("--input", required=True, type=Path, help="BetX normalized runner-level history CSV.")
    ablation_parser.add_argument("--raw-dir", required=True, type=Path, help="Football-Data raw CSV directory.")
    ablation_parser.add_argument("--output-dir", required=True, type=Path, help="Directory where run folders are created.")
    ablation_parser.add_argument("--test-size", type=float, default=0.20)
    ablation_parser.add_argument("--validation-size", type=float, default=0.15)
    ablation_parser.add_argument("--random-seed", type=int, default=42)
    ablation_parser.add_argument("--stake", type=float, default=5.0)
    ablation_parser.add_argument("--commission-rate", type=float, default=0.05)
    ablation_parser.add_argument("--slippage-rate", type=float, default=0.02)
    ablation_parser.add_argument("--run-id", default=None)

    predict_parser = subparsers.add_parser("predict", help="Generate predictions with a saved model.")
    predict_parser.add_argument("--model", required=True, type=Path, help="Path to model.joblib.")
    predict_parser.add_argument("--input", required=True, type=Path, help="BetX normalized runner-level history CSV.")
    predict_parser.add_argument("--output", required=True, type=Path, help="Prediction CSV output path.")
    predict_parser.add_argument("--raw-dir", type=Path, default=None)
    predict_parser.add_argument(
        "--feature-set",
        default="odds_only",
        choices=["odds_only", "odds_elo", "odds_form", "odds_goals", "odds_elo_form", "odds_team_strength_all"],
    )

    return parser


def ablation(args: argparse.Namespace) -> None:
    dataset = load_markets(args.input, raw_dir=args.raw_dir, strict_raw_join=True)
    run_dir = create_run_dir(args.output_dir, args.run_id)
    result = run_ablation(
        dataset.markets,
        random_seed=args.random_seed,
        validation_size=args.validation_size,
        test_size=args.test_size,
        stake=args.stake,
        commission_rate=args.commission_rate,
        slippage_rate=args.slippage_rate,
    )
    write_csv(run_dir / "ablation_results.csv", result.artifacts["ablation_results"])
    write_json(run_dir / "ablation_summary.json", result.artifacts["ablation_summary"])
    write_csv(run_dir / "feature_importance.csv", result.artifacts["feature_importance"])
    write_csv(run_dir / "model_comparison.csv", result.artifacts["model_comparison"])
    write_csv(run_dir / "fold_comparison.csv", result.artifacts["fold_comparison"])
    write_csv(run_dir / "edge_diagnostics_validation.csv", result.artifacts["edge_diagnostics_validation"])
    write_csv(run_dir / "segment_diagnostics_ablation.csv", result.artifacts["segment_diagnostics_ablation"])
    write_json(run_dir / "dataset_quality.json", dataset.quality.to_dict())
    summary = result.artifacts["ablation_summary"]
    family_lines = []
    for status, feature_sets in summary["feature_sets_by_status"].items():
        for feature_set in feature_sets:
            family_lines.append(f"- {feature_set}: {status}")
    print(
        "\n".join(
            [
                "Ablation completed",
                "",
                "Best validation model:",
                str(summary["best_validation_model"]),
                "",
                "Best feature set:",
                str(summary["best_validation_feature_set"]),
                "",
                "Feature families:",
                *family_lines,
                "",
                "Result:",
                "FEATURE_SIGNAL_FOUND" if summary["feature_signal_found"] else "NO_FEATURE_FAMILY_BEATS_ODDS_ONLY",
                f"Run directory: {run_dir}",
            ]
        )
    )


def train(args: argparse.Namespace) -> None:
    dataset = load_markets(args.input, raw_dir=args.raw_dir, strict_raw_join=not args.diagnostic_only_raw_join)
    feature_set = "odds_team_strength_all" if args.raw_dir is not None else "odds_only"
    markets = add_features(dataset.markets, feature_set=feature_set)
    split = chronological_split(markets, validation_size=args.validation_size, test_size=args.test_size)
    run_dir = create_run_dir(args.output_dir, args.run_id)

    configs = _available_model_configs(feature_set)
    model_results: dict[str, dict[str, object]] = {}
    trained_models: dict[str, object] = {}
    prediction_frames: dict[str, pd.DataFrame] = {}
    market_validation_probabilities = split.validation[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
    market_test_probabilities = split.test[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
    market_metrics = {
        "validation": evaluate_probabilities(split.validation["actual_result"], market_validation_probabilities),
        "test": evaluate_probabilities(split.test["actual_result"], market_test_probabilities),
    }
    for config in configs:
        model = train_model(
            split.train,
            model_name=str(config["model_name"]),
            feature_set=str(config["feature_set"]),
            random_seed=args.random_seed,
        )
        trained_models[str(config["name"])] = model
        predictions = pd.concat(
            [
                _prediction_frame(model, split.train, "train", str(config["feature_set"]), str(config["name"])),
                _prediction_frame(model, split.validation, "validation", str(config["feature_set"]), str(config["name"])),
                _prediction_frame(model, split.test, "test", str(config["feature_set"]), str(config["name"])),
            ],
            ignore_index=True,
        )
        prediction_frames[str(config["name"])] = predictions
        validation_probs = predictions[predictions["split"] == "validation"][
            ["model_home_probability", "model_draw_probability", "model_away_probability"]
        ].to_numpy()
        test_probs = predictions[predictions["split"] == "test"][
            ["model_home_probability", "model_draw_probability", "model_away_probability"]
        ].to_numpy()
        model_results[str(config["name"])] = {
            "model_name": config["model_name"],
            "feature_set": config["feature_set"],
            "features": feature_columns(str(config["feature_set"])),
            "validation": evaluate_probabilities(split.validation["actual_result"], validation_probs),
            "test": evaluate_probabilities(split.test["actual_result"], test_probs),
        }

    selected_model_name = _select_model(model_results)
    selected_config = next(config for config in configs if config["name"] == selected_model_name)
    selected_predictions = prediction_frames[selected_model_name]
    model = trained_models[selected_model_name]
    save_model(model, run_dir / "model.joblib")

    validation_predictions = selected_predictions[selected_predictions["split"] == "validation"].reset_index(drop=True)
    validation_sensitivity = edge_sensitivity(
        validation_predictions,
        EDGE_THRESHOLDS,
        stake=args.stake,
        slippage_rate=args.slippage_rate,
        commission_rate=args.commission_rate,
    )
    selected_edge_threshold = select_edge_threshold(validation_sensitivity)
    out_of_sample_predictions = selected_predictions[selected_predictions["split"] == "test"].reset_index(drop=True)
    bets = settle_bets(
        select_value_bets(out_of_sample_predictions, min_edge=selected_edge_threshold, stake=args.stake, slippage_rate=args.slippage_rate),
        commission_rate=args.commission_rate,
    )

    walk_forward = _walk_forward_results(markets, configs, args.random_seed, args.test_size)
    result_label = _result_label(model_results[selected_model_name]["test"], market_metrics["test"], walk_forward, selected_model_name)

    metrics = {
        "result": result_label,
        "dataset": {
            "rows": int(len(markets)),
            "train": int(len(split.train)),
            "validation": int(len(split.validation)),
            "test": int(len(split.test)),
        },
        "market_baseline": market_metrics,
        "models": model_results,
        "selected_model": selected_model_name,
        "selected_feature_set": selected_config["feature_set"],
        "selected_edge_threshold": selected_edge_threshold,
        "validation_edge_sensitivity_only": True,
        "value_betting": summarize_bets(bets),
        "walk_forward": walk_forward.to_dict(orient="records"),
    }

    write_predictions(run_dir / "predictions.csv", selected_predictions)
    write_csv(run_dir / "bets.csv", bets)
    write_csv(run_dir / "walk_forward_results.csv", walk_forward)
    write_csv(run_dir / "validation_edge_sensitivity.csv", validation_sensitivity)
    write_csv(run_dir / "segment_diagnostics.csv", segment_bets(bets))
    write_csv(run_dir / "raw_join_diagnostics.csv", _raw_join_diagnostics(markets))
    write_json(run_dir / "metrics.json", metrics)
    write_json(run_dir / "dataset_quality.json", dataset.quality.to_dict())
    write_json(
        run_dir / "feature_manifest.json",
        {
            "format_version": 2,
            "features": feature_columns(str(selected_config["feature_set"])),
            "available_feature_sets": {
                "odds_only": feature_columns("odds_only"),
                "team_strength_v1": feature_columns("odds_team_strength_all"),
            },
            "target": "actual_result",
            "predictive_odds_source": "opening-bookmaker",
            "closing_odds_usage": "CLV only",
            "clv_convention": "BACK bets use back_clv_ratio = execution_odds / closing_odds; values above 1.0 beat closing odds.",
            "inactivity_decay": {
                "starts_after_days": 30,
                "retention_per_30_days": 0.90,
                "baseline_elo": 1500.0,
            },
            "leakage_controls": [
                "Only opening-bookmaker odds are used as model input.",
                "Closing odds are joined after prediction only for CLV metrics.",
                "Splits are chronological by market_start_time with no shuffle.",
                "Team-strength features are emitted before state updates and grouped by calendar day.",
                "Edge sensitivity is run only on validation; test receives one frozen validation-selected threshold.",
            ],
        },
    )
    write_json(
        run_dir / "model_metadata.json",
        {
            "algorithm": selected_config["model_name"],
            "model": selected_model_name,
            "feature_set": selected_config["feature_set"],
            "features": feature_columns(str(selected_config["feature_set"])),
            "trained_at": datetime.now(UTC).isoformat(),
            "training_period": _period(split.train),
            "validation_period": _period(split.validation),
            "test_period": _period(split.test),
            "rows": {"train": len(split.train), "validation": len(split.validation), "test": len(split.test)},
            "random_seed": args.random_seed,
            "selected_edge_threshold": selected_edge_threshold,
            "format_version": 2,
            "dataset_hash": _sha256(args.input),
            "raw_dir": str(args.raw_dir) if args.raw_dir is not None else None,
        },
    )

    print(_summary_text(metrics, run_dir))


def predict(args: argparse.Namespace) -> None:
    dataset = load_markets(args.input, raw_dir=args.raw_dir)
    markets = add_features(dataset.markets, feature_set=args.feature_set)
    model = load_model(args.model)
    predictions = _prediction_frame(model, markets, "prediction", args.feature_set, "loaded_model")
    write_predictions(args.output, predictions)
    print(f"Wrote {len(predictions)} predictions to {args.output}")


def _prediction_frame(model: object, frame: pd.DataFrame, split: str, feature_set: str = "odds_only", model_name: str = "model") -> pd.DataFrame:
    probabilities = predict_probabilities(model, frame, feature_set=feature_set)
    result = frame.copy()
    result["model_home_probability"] = probabilities[:, 0]
    result["model_draw_probability"] = probabilities[:, 1]
    result["model_away_probability"] = probabilities[:, 2]
    result["predicted_result"] = [LABELS[index] for index in np.argmax(probabilities, axis=1)]
    result["split"] = split
    result["model_name"] = model_name
    return result


def _walk_forward_results(
    markets: pd.DataFrame,
    configs: list[dict[str, str]],
    random_seed: int,
    test_fraction: float,
    max_folds: int = 3,
) -> pd.DataFrame:
    target_fold_size = int(len(markets) * test_fraction)
    fold_size = max(1, min(target_fold_size, max(1, len(markets) // (max_folds + 8))))
    min_train = max(1, len(markets) - (fold_size * (max_folds + 1)))
    rows: list[dict[str, object]] = []
    for index, (train_frame, test_frame) in enumerate(
        walk_forward_splits(markets, min_train_size=min_train, test_size=fold_size, max_folds=max_folds),
        start=1,
    ):
        market_probabilities = test_frame[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
        market_metrics = evaluate_probabilities(test_frame["actual_result"], market_probabilities)
        for config in configs:
            model = train_model(
                train_frame,
                model_name=str(config["model_name"]),
                feature_set=str(config["feature_set"]),
                random_seed=random_seed,
            )
            model_probabilities = predict_probabilities(model, test_frame, feature_set=str(config["feature_set"]))
            model_metrics = evaluate_probabilities(test_frame["actual_result"], model_probabilities)
            rows.append(
                {
                    "fold": index,
                    "model": config["name"],
                    "feature_set": config["feature_set"],
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


def _result_label(model_metrics: dict[str, object], market_metrics: dict[str, object], walk_forward: pd.DataFrame, selected_model: str) -> str:
    test_beats = (
        float(model_metrics["log_loss"]) < float(market_metrics["log_loss"])
        and float(model_metrics["brier_score"]) < float(market_metrics["brier_score"])
    )
    selected_folds = walk_forward[walk_forward["model"] == selected_model] if "model" in walk_forward.columns else walk_forward
    fold_beats = bool(not selected_folds.empty and selected_folds["model_beats_market"].sum() >= 2)
    return "MODEL_BEATS_MARKET_BASELINE" if test_beats and fold_beats else "MODEL_DOES_NOT_BEAT_MARKET_BASELINE"


def _summary_text(metrics: dict[str, object], run_dir: Path) -> str:
    dataset = metrics["dataset"]
    market = metrics["market_baseline"]["test"]
    model = metrics["models"][metrics["selected_model"]]["test"]
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
            f"Selected model: {metrics['selected_model']}",
            f"Log loss: {model['log_loss']:.6f}",
            f"Brier score: {model['brier_score']:.6f}",
            "",
            "Value betting test",
            f"Trades: {bets['trades']}",
            f"Net ROI: {bets['net_roi']:.4f}",
            f"Max drawdown: {bets['max_drawdown']:.4f}",
            f"Median BACK CLV: {bets['median_back_clv_ratio'] if bets['median_back_clv_ratio'] is not None else 'NOT_AVAILABLE'}",
            f"Frozen edge threshold: {metrics['selected_edge_threshold']:.3f}",
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


def _available_model_configs(feature_set: str) -> list[dict[str, str]]:
    if feature_set == "odds_team_strength_all":
        return MODEL_CONFIGS
    return [MODEL_CONFIGS[0]]


def _select_model(model_results: dict[str, dict[str, object]]) -> str:
    rows = []
    for name, result in model_results.items():
        validation = result["validation"]
        rows.append((float(validation["log_loss"]), float(validation["brier_score"]), name))
    rows.sort()
    return rows[0][2]


def _raw_join_diagnostics(markets: pd.DataFrame) -> pd.DataFrame:
    columns = ["market_key", "league", "season", "date", "home_team", "away_team", "raw_join_status", "source_file"]
    if "raw_join_status" not in markets.columns:
        return pd.DataFrame(columns=columns)
    available = [column for column in columns if column in markets.columns]
    return markets[available].copy()


if __name__ == "__main__":
    raise SystemExit(main())
