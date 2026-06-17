from argparse import Namespace
from pathlib import Path

import pandas as pd

from betx_ml.cli import ablation


HEADER = [
    "observed_at",
    "exchange",
    "market_id",
    "market_name",
    "event_name",
    "competition_name",
    "season",
    "odds_source",
    "market_start_time",
    "selection_id",
    "runner_name",
    "best_back_price",
    "best_lay_price",
    "spread",
    "liquidity",
    "result",
]


def test_ablation_cli_writes_required_artifacts(tmp_path: Path):
    output_dir = tmp_path / "runs"
    history = tmp_path / "history.csv"
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    history_rows = []
    raw_rows = []
    for index in range(24):
        date = pd.Timestamp("2024-08-01", tz="UTC") + pd.Timedelta(days=index)
        market_id = f"E0-2024-25-{date.date()}-alpha-beta-{index}"
        actual_selection = [1, 2, 3][index % 3]
        teams = {1: "Alpha", 2: "Draw", 3: "Beta"}
        odds = {1: [1.8, 3.2, 5.0][index % 3], 2: [4.0, 2.9, 4.0][index % 3], 3: [5.0, 3.2, 1.8][index % 3]}
        for source in ("opening-bookmaker", "closing-average"):
            for selection_id in (1, 2, 3):
                history_rows.append(
                    {
                        "observed_at": (date - pd.Timedelta(hours=12)).isoformat(),
                        "exchange": "football-data",
                        "market_id": market_id,
                        "market_name": "Match Odds",
                        "event_name": "Alpha v Beta",
                        "competition_name": "E0",
                        "season": "2024/25",
                        "odds_source": source,
                        "market_start_time": date.isoformat(),
                        "selection_id": selection_id,
                        "runner_name": teams[selection_id],
                        "best_back_price": odds[selection_id],
                        "best_lay_price": odds[selection_id] * 1.04,
                        "spread": 0.04,
                        "liquidity": 1000,
                        "result": "WIN" if selection_id == actual_selection else "LOSE",
                    }
                )
        fthg, ftag, ftr = [(2, 0, "H"), (1, 1, "D"), (0, 2, "A")][index % 3]
        raw_rows.append({"Div": "E0", "Date": date.strftime("%d/%m/%Y"), "Time": "15:00", "HomeTeam": "Alpha", "AwayTeam": "Beta", "FTHG": fthg, "FTAG": ftag, "FTR": ftr})
    pd.DataFrame(history_rows, columns=HEADER).to_csv(history, index=False)
    pd.DataFrame(raw_rows).to_csv(raw_dir / "2425-E0.csv", index=False, encoding="utf-8-sig")

    ablation(
        Namespace(
            input=history,
            raw_dir=raw_dir,
            output_dir=output_dir,
            run_id="ablation-test",
            validation_size=0.20,
            test_size=0.20,
            random_seed=42,
            stake=5.0,
            commission_rate=0.05,
            slippage_rate=0.02,
        )
    )

    run_dir = output_dir / "ablation-test"
    for name in [
        "ablation_results.csv",
        "ablation_summary.json",
        "feature_importance.csv",
        "model_comparison.csv",
        "fold_comparison.csv",
        "edge_diagnostics_validation.csv",
        "segment_diagnostics_ablation.csv",
    ]:
        assert (run_dir / name).exists()
