import numpy as np
import pandas as pd
import pytest

from betx_ml.betting import adjust_odds_profit_haircut, select_value_bets, settle_bets, summarize_bets


def market_predictions() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "market_key": "m1",
                "date": "2024-08-01T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "home_team": "Alpha",
                "away_team": "Beta",
                "actual_result": "HOME",
                "home_odds": 2.0,
                "draw_odds": 3.4,
                "away_odds": 4.0,
                "closing_home_odds": 1.9,
                "closing_draw_odds": 3.2,
                "closing_away_odds": 4.2,
                "model_home_probability": 0.62,
                "model_draw_probability": 0.25,
                "model_away_probability": 0.13,
            },
            {
                "market_key": "m2",
                "date": "2024-08-02T15:00:00Z",
                "league": "E0",
                "season": "2024/25",
                "home_team": "Gamma",
                "away_team": "Delta",
                "actual_result": "AWAY",
                "home_odds": 1.8,
                "draw_odds": 3.5,
                "away_odds": 5.0,
                "closing_home_odds": np.nan,
                "closing_draw_odds": np.nan,
                "closing_away_odds": np.nan,
                "model_home_probability": 0.40,
                "model_draw_probability": 0.27,
                "model_away_probability": 0.33,
            },
        ]
    )


def test_adjust_odds_uses_java_profit_haircut_formula():
    assert adjust_odds_profit_haircut(4.0, 0.02) == pytest.approx(3.94)


def test_selects_only_highest_edge_bet_per_market_after_slippage():
    bets = select_value_bets(market_predictions(), min_edge=0.03, stake=5, slippage_rate=0.02)

    assert bets["market_key"].tolist() == ["m1", "m2"]
    assert bets["selection"].tolist() == ["HOME", "AWAY"]
    assert bets.loc[0, "execution_odds"] == pytest.approx(1.98)
    assert bets.loc[0, "expected_value"] == pytest.approx(0.62 * 1.98 - 1)


def test_settlement_applies_commission_only_to_positive_gross_pnl_and_preserves_missing_clv():
    settled = settle_bets(select_value_bets(market_predictions(), min_edge=0.03, stake=5, slippage_rate=0.02), commission_rate=0.05)

    assert settled.loc[0, "gross_pnl"] == pytest.approx(4.9)
    assert settled.loc[0, "commission"] == pytest.approx(0.245)
    assert settled.loc[0, "net_pnl"] == pytest.approx(4.655)
    assert settled.loc[1, "gross_pnl"] == pytest.approx(19.6)
    assert settled.loc[1, "commission"] == pytest.approx(0.98)
    assert pd.isna(settled.loc[1, "decimal_clv_ratio"])


def test_summarizes_bets_with_roi_and_drawdown():
    settled = settle_bets(select_value_bets(market_predictions(), min_edge=0.03, stake=5, slippage_rate=0.02), commission_rate=0.05)

    summary = summarize_bets(settled)

    assert summary["trades"] == 2
    assert summary["wins"] == 2
    assert summary["losses"] == 0
    assert summary["total_staked"] == pytest.approx(10)
    assert summary["net_pnl"] == pytest.approx(23.275)
    assert summary["net_roi"] == pytest.approx(2.3275)
    assert summary["max_drawdown"] == pytest.approx(0.0)
    assert summary["median_clv"] == pytest.approx(1.9 / 1.98)
    assert summary["positive_clv_rate"] == pytest.approx(0.0)

