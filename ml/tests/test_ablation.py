import numpy as np
import pandas as pd
import pytest

from betx_ml.ablation import (
    ABLATION_MODEL_CONFIGS,
    classify_ablation_status,
    run_ablation,
    select_best_configuration,
    summarize_ablation,
)


def test_required_ablation_model_matrix_skips_hgb_odds_only():
    pairs = {(config.model, config.feature_set) for config in ABLATION_MODEL_CONFIGS}

    assert ("logistic_regression", "odds_only") in pairs
    assert ("logistic_regression", "odds_elo") in pairs
    assert ("logistic_regression", "odds_form") in pairs
    assert ("logistic_regression", "odds_goals") in pairs
    assert ("logistic_regression", "odds_elo_form") in pairs
    assert ("logistic_regression", "odds_team_strength_all") in pairs
    assert ("hist_gradient_boosting", "odds_only") not in pairs
    assert ("hist_gradient_boosting", "odds_team_strength_all") in pairs


def test_select_best_configuration_uses_validation_not_test():
    rows = pd.DataFrame(
        [
            {
                "model": "logistic_regression",
                "feature_set": "odds_only",
                "validation_log_loss": 0.98,
                "validation_brier": 0.58,
                "test_log_loss": 0.90,
            },
            {
                "model": "logistic_regression",
                "feature_set": "odds_elo",
                "validation_log_loss": 0.97,
                "validation_brier": 0.59,
                "test_log_loss": 1.10,
            },
        ]
    )

    best = select_best_configuration(rows)

    assert best["feature_set"] == "odds_elo"


@pytest.mark.parametrize(
    ("row", "expected"),
    [
        ({"delta_vs_odds_only_log_loss": -0.01, "delta_vs_odds_only_brier": 0.0, "folds_beating_market": 2, "dominant_segment_dependency": False}, "IMPROVES_OVER_ODDS_ONLY"),
        ({"delta_vs_odds_only_log_loss": 0.0001, "delta_vs_odds_only_brier": 0.0001, "folds_beating_market": 1, "dominant_segment_dependency": False}, "NEUTRAL"),
        ({"delta_vs_odds_only_log_loss": 0.02, "delta_vs_odds_only_brier": 0.02, "folds_beating_market": 0, "dominant_segment_dependency": False}, "DEGRADES"),
        ({"delta_vs_odds_only_log_loss": -0.02, "delta_vs_odds_only_brier": 0.0, "folds_beating_market": 2, "dominant_segment_dependency": True}, "UNSTABLE"),
    ],
)
def test_classifies_ablation_status(row, expected):
    assert classify_ablation_status(pd.Series(row)) == expected


def test_summary_groups_feature_sets_and_recommends_next_step():
    results = pd.DataFrame(
        [
            {"model": "logistic_regression", "feature_set": "odds_only", "validation_log_loss": 0.98, "validation_brier": 0.58, "ablation_status": "NEUTRAL"},
            {"model": "logistic_regression", "feature_set": "odds_elo", "validation_log_loss": 0.97, "validation_brier": 0.57, "ablation_status": "IMPROVES_OVER_ODDS_ONLY"},
        ]
    )

    summary = summarize_ablation(results)

    assert summary["best_validation_model"] == "logistic_regression"
    assert summary["best_validation_feature_set"] == "odds_elo"
    assert summary["feature_signal_found"] is True
    assert summary["recommendation"] == "ML-002.2"
    assert summary["feature_sets_by_status"]["IMPROVES_OVER_ODDS_ONLY"] == ["odds_elo"]


def tiny_markets() -> pd.DataFrame:
    rows = []
    teams = ["Alpha", "Beta", "Gamma", "Delta"]
    for index in range(24):
        home = teams[index % len(teams)]
        away = teams[(index + 1) % len(teams)]
        actual = ["HOME", "DRAW", "AWAY"][index % 3]
        fthg, ftag, ftr = [(2, 0, "H"), (1, 1, "D"), (0, 2, "A")][index % 3]
        rows.append(
            {
                "market_key": f"k{index}",
                "date": pd.Timestamp("2024-08-01", tz="UTC") + pd.Timedelta(days=index),
                "match_date": (pd.Timestamp("2024-08-01") + pd.Timedelta(days=index)).date(),
                "league": "E0" if index % 2 == 0 else "SP1",
                "season": "2024/25",
                "home_team": home,
                "away_team": away,
                "home_team_key": home.lower(),
                "away_team_key": away.lower(),
                "actual_result": actual,
                "fthg": fthg,
                "ftag": ftag,
                "ftr": ftr,
                "home_odds": [1.8, 3.2, 5.0][index % 3],
                "draw_odds": [4.0, 2.9, 4.0][index % 3],
                "away_odds": [5.0, 3.2, 1.8][index % 3],
                "closing_home_odds": [1.75, 3.3, 5.2][index % 3],
                "closing_draw_odds": [4.1, 2.8, 4.1][index % 3],
                "closing_away_odds": [5.2, 3.3, 1.75][index % 3],
            }
        )
    return pd.DataFrame(rows)


def test_run_ablation_reuses_one_split_and_exports_required_tables():
    result = run_ablation(
        tiny_markets(),
        random_seed=42,
        validation_size=0.20,
        test_size=0.20,
        thresholds=[0.01, 0.03],
        max_folds=2,
        importance_repeats=1,
    )

    assert set(result.artifacts) == {
        "ablation_results",
        "ablation_summary",
        "feature_importance",
        "model_comparison",
        "fold_comparison",
        "edge_diagnostics_validation",
        "segment_diagnostics_ablation",
    }
    assert len(result.artifacts["ablation_results"]) == len(ABLATION_MODEL_CONFIGS)
    assert result.artifacts["ablation_results"]["selected_threshold"].notna().all()
    assert set(result.artifacts["edge_diagnostics_validation"]["threshold"]) == {0.01, 0.03}
    assert result.artifacts["ablation_summary"]["best_validation_feature_set"] in set(result.artifacts["ablation_results"]["feature_set"])
