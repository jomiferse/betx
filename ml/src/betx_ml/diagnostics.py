from __future__ import annotations

import math

import pandas as pd


ODDS_BUCKETS = [
    (1.0, 2.0, "1.00-1.99"),
    (2.0, 3.0, "2.00-2.99"),
    (3.0, 5.0, "3.00-4.99"),
    (5.0, 7.5, "5.00-7.49"),
    (7.5, 10.0, "7.50-9.99"),
    (10.0, math.inf, "10.00+"),
]
EDGE_BUCKETS = [
    (-math.inf, 0.01, "<1.0%"),
    (0.01, 0.02, "1.0%-2.0%"),
    (0.02, 0.03, "2.0%-3.0%"),
    (0.03, 0.05, "3.0%-5.0%"),
    (0.05, 0.075, "5.0%-7.5%"),
    (0.075, 0.10, "7.5%-10.0%"),
    (0.10, math.inf, "10.0%+"),
]


def odds_bucket(value: float) -> str:
    return _bucket(float(value), ODDS_BUCKETS)


def edge_bucket(value: float) -> str:
    return _bucket(float(value), EDGE_BUCKETS)


def segment_bets(bets: pd.DataFrame) -> pd.DataFrame:
    columns = [
        "segment_type",
        "segment",
        "trades",
        "wins",
        "losses",
        "total_staked",
        "net_pnl",
        "net_roi",
        "max_drawdown",
        "median_back_clv_ratio",
        "positive_back_clv_rate",
    ]
    if bets.empty:
        return pd.DataFrame(columns=columns)

    frame = bets.copy()
    frame["period"] = pd.to_datetime(frame["date"], utc=True).dt.tz_localize(None).dt.to_period("M").astype(str)
    frame["odds_bucket"] = frame["execution_odds"].map(odds_bucket)
    frame["edge_bucket"] = frame["expected_value"].map(edge_bucket)

    rows: list[dict[str, object]] = []
    for segment_type in ("selection", "league", "season", "period", "odds_bucket", "edge_bucket"):
        for segment, group in frame.groupby(segment_type, sort=True, dropna=False):
            stake = float(group["stake"].sum())
            clv = group["back_clv_ratio"].dropna()
            rows.append(
                {
                    "segment_type": segment_type,
                    "segment": str(segment),
                    "trades": int(len(group)),
                    "wins": int(group["won"].sum()),
                    "losses": int(len(group) - int(group["won"].sum())),
                    "total_staked": stake,
                    "net_pnl": float(group["net_pnl"].sum()),
                    "net_roi": float(group["net_pnl"].sum() / stake) if stake else 0.0,
                    "max_drawdown": _max_drawdown(group["net_pnl"].tolist()),
                    "median_back_clv_ratio": float(clv.median()) if not clv.empty else None,
                    "positive_back_clv_rate": float((clv > 1.0).mean()) if not clv.empty else None,
                }
            )
    return pd.DataFrame(rows, columns=columns)


def _bucket(value: float, buckets: list[tuple[float, float, str]]) -> str:
    for low, high, label in buckets:
        if low <= value < high:
            return label
    return buckets[-1][2]


def _max_drawdown(pnls: list[float]) -> float:
    equity = 0.0
    peak = 0.0
    drawdown = 0.0
    for pnl in pnls:
        equity += float(pnl)
        peak = max(peak, equity)
        drawdown = max(drawdown, peak - equity)
    return drawdown
