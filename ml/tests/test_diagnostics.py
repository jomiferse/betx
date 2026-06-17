import numpy as np
import pandas as pd
import pytest

from betx_ml.diagnostics import edge_bucket, odds_bucket, segment_bets


def settled_bets() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "date": "2024-08-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "selection": "HOME",
                "opening_odds": 2.0,
                "execution_odds": 1.98,
                "expected_value": 0.031,
                "stake": 5.0,
                "won": True,
                "net_pnl": 4.655,
                "back_clv_ratio": 1.04,
            },
            {
                "date": "2024-09-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "selection": "AWAY",
                "opening_odds": 6.0,
                "execution_odds": 5.9,
                "expected_value": 0.09,
                "stake": 5.0,
                "won": False,
                "net_pnl": -5.0,
                "back_clv_ratio": np.nan,
            },
        ]
    )


def test_bucket_labels_are_stable():
    assert odds_bucket(1.5) == "1.00-1.99"
    assert odds_bucket(2.5) == "2.00-2.99"
    assert odds_bucket(6.0) == "5.00-7.49"
    assert edge_bucket(0.031) == "3.0%-5.0%"
    assert edge_bucket(0.09) == "7.5%-10.0%"


def test_segments_include_selection_league_season_period_odds_and_edge():
    segments = segment_bets(settled_bets())

    keys = {(row["segment_type"], row["segment"]) for row in segments.to_dict(orient="records")}
    assert ("selection", "HOME") in keys
    assert ("league", "E0") in keys
    assert ("season", "2024/25") in keys
    assert ("period", "2024-08") in keys
    assert ("odds_bucket", "1.00-1.99") in keys
    assert ("edge_bucket", "3.0%-5.0%") in keys

    home = segments[(segments["segment_type"] == "selection") & (segments["segment"] == "HOME")].iloc[0]
    assert home["trades"] == 1
    assert home["wins"] == 1
    assert home["net_pnl"] == pytest.approx(4.655)
    assert home["net_roi"] == pytest.approx(4.655 / 5.0)
    assert home["median_back_clv_ratio"] == pytest.approx(1.04)
