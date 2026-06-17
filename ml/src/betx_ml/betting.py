from __future__ import annotations

import math

import pandas as pd

from betx_ml.features import LABELS


LOWER = {"HOME": "home", "DRAW": "draw", "AWAY": "away"}


def adjust_odds_profit_haircut(odds: float, slippage_rate: float) -> float:
    return 1.0 + ((float(odds) - 1.0) * (1.0 - float(slippage_rate)))


def select_value_bets(
    predictions: pd.DataFrame,
    min_edge: float = 0.03,
    stake: float = 5.0,
    slippage_rate: float = 0.02,
) -> pd.DataFrame:
    bets: list[dict[str, object]] = []
    for row in predictions.itertuples(index=False):
        candidates: list[dict[str, object]] = []
        for label in LABELS:
            lower = LOWER[label]
            odds = float(getattr(row, f"{lower}_odds"))
            probability = float(getattr(row, f"model_{lower}_probability"))
            execution_odds = adjust_odds_profit_haircut(odds, slippage_rate)
            edge = probability * execution_odds - 1.0
            if edge >= min_edge:
                candidates.append(
                    {
                        "market_key": row.market_key,
                        "date": row.date,
                        "league": row.league,
                        "season": row.season,
                        "home_team": row.home_team,
                        "away_team": row.away_team,
                        "actual_result": row.actual_result,
                        "selection": label,
                        "predicted_probability": probability,
                        "opening_odds": odds,
                        "execution_odds": execution_odds,
                        "closing_odds": getattr(row, f"closing_{lower}_odds", math.nan),
                        "expected_value": edge,
                        "stake": stake,
                    }
                )
        if candidates:
            bets.append(max(candidates, key=lambda candidate: candidate["expected_value"]))
    return pd.DataFrame(bets)


def settle_bets(bets: pd.DataFrame, commission_rate: float = 0.05) -> pd.DataFrame:
    if bets.empty:
        return bets.copy()
    settled = bets.copy()
    settled["won"] = settled["selection"] == settled["actual_result"]
    settled["gross_pnl"] = settled.apply(
        lambda row: row["stake"] * (row["execution_odds"] - 1.0) if row["won"] else -row["stake"],
        axis=1,
    )
    settled["commission"] = settled["gross_pnl"].apply(lambda pnl: max(float(pnl), 0.0) * commission_rate)
    settled["net_pnl"] = settled["gross_pnl"] - settled["commission"]
    settled["decimal_clv_ratio"] = settled.apply(
        lambda row: row["closing_odds"] / row["execution_odds"] if pd.notna(row["closing_odds"]) else math.nan,
        axis=1,
    )
    settled["implied_probability_change"] = settled.apply(
        lambda row: (1.0 / row["closing_odds"]) - (1.0 / row["execution_odds"]) if pd.notna(row["closing_odds"]) else math.nan,
        axis=1,
    )
    return settled


def summarize_bets(bets: pd.DataFrame) -> dict[str, object]:
    if bets.empty:
        return {
            "trades": 0,
            "wins": 0,
            "losses": 0,
            "total_staked": 0.0,
            "gross_pnl": 0.0,
            "net_pnl": 0.0,
            "net_roi": 0.0,
            "strike_rate": 0.0,
            "max_drawdown": 0.0,
            "average_odds": None,
            "average_edge": None,
            "median_clv": None,
            "positive_clv_rate": None,
        }
    total_staked = float(bets["stake"].sum())
    wins = int(bets["won"].sum())
    clv = bets["decimal_clv_ratio"].dropna()
    return {
        "trades": int(len(bets)),
        "wins": wins,
        "losses": int(len(bets) - wins),
        "total_staked": total_staked,
        "gross_pnl": float(bets["gross_pnl"].sum()),
        "net_pnl": float(bets["net_pnl"].sum()),
        "net_roi": float(bets["net_pnl"].sum() / total_staked) if total_staked else 0.0,
        "strike_rate": wins / len(bets) if len(bets) else 0.0,
        "max_drawdown": _max_drawdown(bets["net_pnl"].tolist()),
        "average_odds": float(bets["execution_odds"].mean()),
        "average_edge": float(bets["expected_value"].mean()),
        "median_clv": float(clv.median()) if not clv.empty else None,
        "positive_clv_rate": float((clv > 1.0).mean()) if not clv.empty else None,
        "by_selection": _group_summary(bets, "selection"),
        "by_league": _group_summary(bets, "league"),
        "by_period": _group_summary(
            bets.assign(period=pd.to_datetime(bets["date"], utc=True).dt.tz_localize(None).dt.to_period("M").astype(str)),
            "period",
        ),
    }


def _max_drawdown(pnls: list[float]) -> float:
    equity = 0.0
    peak = 0.0
    drawdown = 0.0
    for pnl in pnls:
        equity += float(pnl)
        peak = max(peak, equity)
        drawdown = max(drawdown, peak - equity)
    return drawdown


def _group_summary(bets: pd.DataFrame, column: str) -> dict[str, dict[str, float | int]]:
    result: dict[str, dict[str, float | int]] = {}
    for value, group in bets.groupby(column, sort=True):
        stake = float(group["stake"].sum())
        result[str(value)] = {
            "trades": int(len(group)),
            "wins": int(group["won"].sum()),
            "net_pnl": float(group["net_pnl"].sum()),
            "net_roi": float(group["net_pnl"].sum() / stake) if stake else 0.0,
        }
    return result
