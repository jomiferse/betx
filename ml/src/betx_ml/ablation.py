from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from betx_ml.betting import select_value_bets, settle_bets, summarize_bets
from betx_ml.diagnostics import edge_bucket, odds_bucket, segment_bets
from betx_ml.evaluation import edge_sensitivity, evaluate_probabilities, select_edge_threshold, walk_forward_splits
from betx_ml.feature_importance import export_feature_importance
from betx_ml.features import add_features, chronological_split, feature_columns
from betx_ml.models import predict_probabilities, train_model


@dataclass(frozen=True)
class AblationModelConfig:
    model: str
    feature_set: str

    @property
    def key(self) -> str:
        return f"{self.model}__{self.feature_set}"


ABLATION_FEATURE_SETS = [
    "odds_only",
    "odds_elo",
    "odds_form",
    "odds_goals",
    "odds_elo_form",
    "odds_team_strength_all",
]
ABLATION_MODEL_CONFIGS = [
    *(AblationModelConfig("logistic_regression", feature_set) for feature_set in ABLATION_FEATURE_SETS),
    *(AblationModelConfig("hist_gradient_boosting", feature_set) for feature_set in ABLATION_FEATURE_SETS if feature_set != "odds_only"),
]
ABLATION_STATUSES = ["IMPROVES_OVER_ODDS_ONLY", "NEUTRAL", "DEGRADES", "UNSTABLE"]
EDGE_THRESHOLDS = [0.01, 0.02, 0.03, 0.05, 0.075, 0.10]


@dataclass(frozen=True)
class AblationRunResult:
    artifacts: dict[str, object]


def select_best_configuration(results: pd.DataFrame) -> pd.Series:
    if results.empty:
        raise ValueError("Ablation results are empty")
    ordered = results.sort_values(["validation_log_loss", "validation_brier", "model", "feature_set"], ascending=True)
    return ordered.iloc[0]


def classify_ablation_status(row: pd.Series, tolerance: float = 0.001) -> str:
    if bool(row.get("dominant_segment_dependency", False)):
        return "UNSTABLE"
    log_delta = float(row.get("delta_vs_odds_only_log_loss", 0.0))
    brier_delta = float(row.get("delta_vs_odds_only_brier", 0.0))
    folds = int(row.get("folds_beating_market", 0))
    if log_delta < -tolerance and brier_delta <= tolerance and folds >= 2:
        return "IMPROVES_OVER_ODDS_ONLY"
    if log_delta > tolerance or brier_delta > tolerance:
        return "DEGRADES"
    return "NEUTRAL"


def summarize_ablation(results: pd.DataFrame) -> dict[str, object]:
    best = select_best_configuration(results)
    grouped = {
        status: sorted(results.loc[results["ablation_status"] == status, "feature_set"].drop_duplicates().tolist())
        for status in ABLATION_STATUSES
    }
    signal_found = bool(grouped["IMPROVES_OVER_ODDS_ONLY"])
    baseline = results[(results["model"] == "logistic_regression") & (results["feature_set"] == "odds_only")]
    return {
        "best_validation_model": str(best["model"]),
        "best_validation_feature_set": str(best["feature_set"]),
        "baseline_odds_only": baseline.iloc[0].to_dict() if not baseline.empty else None,
        "feature_sets_by_status": grouped,
        "feature_signal_found": signal_found,
        "recommendation": "ML-002.2" if signal_found else "PAUSE_CURRENT_ML_FEATURES",
    }


def run_ablation(
    markets: pd.DataFrame,
    *,
    random_seed: int = 42,
    validation_size: float = 0.15,
    test_size: float = 0.20,
    thresholds: list[float] | None = None,
    stake: float = 5.0,
    commission_rate: float = 0.05,
    slippage_rate: float = 0.02,
    max_folds: int = 3,
    importance_repeats: int = 5,
) -> AblationRunResult:
    thresholds = thresholds or EDGE_THRESHOLDS
    featured = add_features(markets, feature_set="odds_team_strength_all")
    split = chronological_split(featured, validation_size=validation_size, test_size=test_size)
    market_validation = split.validation[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
    market_test = split.test[["market_home_probability", "market_draw_probability", "market_away_probability"]].to_numpy()
    market_metrics = {
        "validation": evaluate_probabilities(split.validation["actual_result"], market_validation),
        "test": evaluate_probabilities(split.test["actual_result"], market_test),
    }

    result_rows: list[dict[str, object]] = []
    comparison_rows: list[dict[str, object]] = []
    edge_rows: list[pd.DataFrame] = []
    segment_rows: list[pd.DataFrame] = []
    importance_rows: list[pd.DataFrame] = []
    predictions_by_key: dict[str, pd.DataFrame] = {}
    models_by_key: dict[str, object] = {}

    for config in ABLATION_MODEL_CONFIGS:
        model = train_model(split.train, model_name=config.model, feature_set=config.feature_set, random_seed=random_seed)
        models_by_key[config.key] = model
        validation_predictions = _prediction_frame(model, split.validation, "validation", config)
        test_predictions = _prediction_frame(model, split.test, "test", config)
        predictions_by_key[config.key] = pd.concat([validation_predictions, test_predictions], ignore_index=True)
        validation_probabilities = validation_predictions[["model_home_probability", "model_draw_probability", "model_away_probability"]].to_numpy()
        test_probabilities = test_predictions[["model_home_probability", "model_draw_probability", "model_away_probability"]].to_numpy()
        validation_metrics = evaluate_probabilities(split.validation["actual_result"], validation_probabilities)
        test_metrics = evaluate_probabilities(split.test["actual_result"], test_probabilities)
        sensitivity = edge_sensitivity(
            validation_predictions,
            thresholds,
            stake=stake,
            slippage_rate=slippage_rate,
            commission_rate=commission_rate,
        )
        sensitivity.insert(0, "feature_set", config.feature_set)
        sensitivity.insert(0, "model", config.model)
        edge_rows.append(sensitivity)
        selected_threshold = select_edge_threshold(sensitivity)
        bets = settle_bets(
            select_value_bets(test_predictions, min_edge=selected_threshold, stake=stake, slippage_rate=slippage_rate),
            commission_rate=commission_rate,
        )
        bet_summary = summarize_bets(bets)
        segments = segment_bets(bets)
        if not segments.empty:
            segments.insert(0, "feature_set", config.feature_set)
            segments.insert(0, "model", config.model)
            segment_rows.append(segments)
        importance_rows.append(
            export_feature_importance(
                model,
                config.model,
                config.feature_set,
                split.validation,
                random_seed=random_seed,
                n_repeats=importance_repeats,
            )
        )
        row = {
            "model": config.model,
            "feature_set": config.feature_set,
            "validation_log_loss": validation_metrics["log_loss"],
            "validation_brier": validation_metrics["brier_score"],
            "test_log_loss": test_metrics["log_loss"],
            "test_brier": test_metrics["brier_score"],
            "validation_accuracy": validation_metrics["accuracy"],
            "test_accuracy": test_metrics["accuracy"],
            "delta_vs_market_log_loss": float(test_metrics["log_loss"]) - float(market_metrics["test"]["log_loss"]),
            "delta_vs_market_brier": float(test_metrics["brier_score"]) - float(market_metrics["test"]["brier_score"]),
            "selected_threshold": selected_threshold,
            "test_trades": bet_summary["trades"],
            "test_wins": bet_summary["wins"],
            "test_losses": bet_summary["losses"],
            "test_roi": bet_summary["net_roi"],
            "test_pnl": bet_summary["net_pnl"],
            "test_max_drawdown": bet_summary["max_drawdown"],
            "test_strike_rate": bet_summary["strike_rate"],
            "average_odds": bet_summary["average_odds"],
            "average_predicted_edge": bet_summary["average_edge"],
            "median_back_clv_ratio": bet_summary["median_back_clv_ratio"],
            "median_back_clv_pct": bet_summary["median_back_clv_pct"],
            "positive_back_clv_rate": bet_summary["positive_back_clv_rate"],
        }
        result_rows.append(row)
        comparison_rows.append({**row, "validation_per_class": validation_metrics["per_class"], "test_per_class": test_metrics["per_class"], "test_calibration": test_metrics["calibration"]})

    fold_comparison = _fold_comparison(featured, random_seed, test_size, max_folds=max_folds)
    results = pd.DataFrame(result_rows)
    baseline = results[(results["model"] == "logistic_regression") & (results["feature_set"] == "odds_only")].iloc[0]
    fold_counts = fold_comparison.groupby(["model", "feature_set"])["model_beats_market"].sum().reset_index(name="folds_beating_market")
    results = results.merge(fold_counts, on=["model", "feature_set"], how="left")
    results["folds_beating_market"] = results["folds_beating_market"].fillna(0).astype(int)
    results["delta_vs_odds_only_log_loss"] = results["validation_log_loss"] - float(baseline["validation_log_loss"])
    results["delta_vs_odds_only_brier"] = results["validation_brier"] - float(baseline["validation_brier"])
    results["dominant_segment_dependency"] = False
    results["ablation_status"] = results.apply(classify_ablation_status, axis=1)
    results.loc[(results["model"] == "logistic_regression") & (results["feature_set"] == "odds_only"), "ablation_status"] = "NEUTRAL"
    ordered_columns = [
        "model",
        "feature_set",
        "validation_log_loss",
        "validation_brier",
        "test_log_loss",
        "test_brier",
        "delta_vs_market_log_loss",
        "delta_vs_odds_only_log_loss",
        "folds_beating_market",
        "selected_threshold",
        "test_trades",
        "test_roi",
        "test_pnl",
        "test_max_drawdown",
        "median_back_clv_ratio",
        "median_back_clv_pct",
        "positive_back_clv_rate",
        "ablation_status",
    ]
    ablation_results = results[ordered_columns].copy()
    model_comparison = results.copy()
    summary = summarize_ablation(results)
    return AblationRunResult(
        {
            "ablation_results": ablation_results,
            "ablation_summary": summary,
            "feature_importance": pd.concat(importance_rows, ignore_index=True) if importance_rows else pd.DataFrame(),
            "model_comparison": model_comparison,
            "fold_comparison": fold_comparison,
            "edge_diagnostics_validation": pd.concat(edge_rows, ignore_index=True) if edge_rows else pd.DataFrame(),
            "segment_diagnostics_ablation": pd.concat(segment_rows, ignore_index=True) if segment_rows else pd.DataFrame(),
        }
    )


def _prediction_frame(model: object, frame: pd.DataFrame, split: str, config: AblationModelConfig) -> pd.DataFrame:
    probabilities = predict_probabilities(model, frame, feature_set=config.feature_set)
    result = frame.copy()
    result["model_home_probability"] = probabilities[:, 0]
    result["model_draw_probability"] = probabilities[:, 1]
    result["model_away_probability"] = probabilities[:, 2]
    result["split"] = split
    result["model"] = config.model
    result["feature_set"] = config.feature_set
    return result


def _fold_comparison(markets: pd.DataFrame, random_seed: int, test_fraction: float, max_folds: int) -> pd.DataFrame:
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
        for config in ABLATION_MODEL_CONFIGS:
            model = train_model(train_frame, model_name=config.model, feature_set=config.feature_set, random_seed=random_seed)
            probabilities = predict_probabilities(model, test_frame, feature_set=config.feature_set)
            metrics = evaluate_probabilities(test_frame["actual_result"], probabilities)
            rows.append(
                {
                    "fold": index,
                    "model": config.model,
                    "feature_set": config.feature_set,
                    "train_start": train_frame["date"].min().isoformat(),
                    "train_end": train_frame["date"].max().isoformat(),
                    "test_start": test_frame["date"].min().isoformat(),
                    "test_end": test_frame["date"].max().isoformat(),
                    "market_log_loss": market_metrics["log_loss"],
                    "model_log_loss": metrics["log_loss"],
                    "market_brier": market_metrics["brier_score"],
                    "model_brier": metrics["brier_score"],
                    "model_beats_market": bool(
                        metrics["log_loss"] < market_metrics["log_loss"]
                        and metrics["brier_score"] < market_metrics["brier_score"]
                    ),
                }
            )
    return pd.DataFrame(rows)
