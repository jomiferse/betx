from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path

import pandas as pd


RUNNER_BY_SELECTION = {1: "HOME", 2: "DRAW", 3: "AWAY"}
SELECTION_BY_RESULT = {"HOME": 1, "DRAW": 2, "AWAY": 3}


@dataclass(frozen=True)
class DatasetQuality:
    markets_read: int
    valid_markets: int
    discarded_markets: int
    discard_reasons: dict[str, int]
    date_min: str | None
    date_max: str | None
    leagues: list[str]
    seasons: list[str]

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


@dataclass(frozen=True)
class MarketDataset:
    markets: pd.DataFrame
    quality: DatasetQuality


def normalize_result(selection_id: int) -> str:
    try:
        return RUNNER_BY_SELECTION[int(selection_id)]
    except (KeyError, ValueError) as exc:
        raise ValueError(f"Unsupported selection_id for football result: {selection_id}") from exc


def load_markets(
    input_path: str | Path,
    predictive_odds_source: str = "opening-bookmaker",
    closing_odds_source: str = "closing-average",
) -> MarketDataset:
    rows = pd.read_csv(input_path)
    required = {
        "observed_at",
        "exchange",
        "market_id",
        "event_name",
        "competition_name",
        "season",
        "odds_source",
        "market_start_time",
        "selection_id",
        "runner_name",
        "best_back_price",
        "result",
    }
    missing = sorted(required - set(rows.columns))
    if missing:
        raise ValueError(f"Missing required columns: {', '.join(missing)}")

    rows = rows.copy()
    rows["market_key"] = rows["exchange"].astype(str) + "|" + rows["market_id"].astype(str)
    rows["selection_id"] = pd.to_numeric(rows["selection_id"], errors="coerce").astype("Int64")
    rows["best_back_price"] = pd.to_numeric(rows["best_back_price"], errors="coerce")
    rows["market_start_time"] = pd.to_datetime(rows["market_start_time"], errors="coerce", utc=True)

    opening = rows[rows["odds_source"] == predictive_odds_source]
    closing = rows[rows["odds_source"] == closing_odds_source]
    closing_by_key = {
        key: group for key, group in closing.groupby("market_key", sort=False)
    }

    markets: list[dict[str, object]] = []
    discard_reasons: dict[str, int] = {}
    markets_read = int(opening["market_key"].nunique())

    for market_key, group in opening.groupby("market_key", sort=False):
        reason = _discard_reason(group)
        if reason is not None:
            discard_reasons[reason] = discard_reasons.get(reason, 0) + 1
            continue

        by_selection = {int(row.selection_id): row for row in group.itertuples(index=False)}
        winner_selection = int(group.loc[group["result"].str.upper() == "WIN", "selection_id"].iloc[0])
        close = _closing_odds(closing_by_key.get(market_key))
        home = by_selection[1]
        draw = by_selection[2]
        away = by_selection[3]
        markets.append(
            {
                "market_key": market_key,
                "exchange": home.exchange,
                "market_id": home.market_id,
                "date": home.market_start_time,
                "league": home.competition_name,
                "season": home.season,
                "home_team": home.runner_name,
                "away_team": away.runner_name,
                "event_name": home.event_name,
                "actual_result": normalize_result(winner_selection),
                "home_odds": float(home.best_back_price),
                "draw_odds": float(draw.best_back_price),
                "away_odds": float(away.best_back_price),
                "closing_home_odds": close.get("HOME"),
                "closing_draw_odds": close.get("DRAW"),
                "closing_away_odds": close.get("AWAY"),
            }
        )

    market_frame = pd.DataFrame(markets).sort_values("date").reset_index(drop=True)
    quality = DatasetQuality(
        markets_read=markets_read,
        valid_markets=len(market_frame),
        discarded_markets=sum(discard_reasons.values()),
        discard_reasons=dict(sorted(discard_reasons.items())),
        date_min=_date_string(market_frame["date"].min()) if not market_frame.empty else None,
        date_max=_date_string(market_frame["date"].max()) if not market_frame.empty else None,
        leagues=sorted(market_frame["league"].dropna().astype(str).unique().tolist()) if not market_frame.empty else [],
        seasons=sorted(market_frame["season"].dropna().astype(str).unique().tolist()) if not market_frame.empty else [],
    )
    return MarketDataset(market_frame, quality)


def _discard_reason(group: pd.DataFrame) -> str | None:
    selections = group["selection_id"].dropna().astype(int).tolist()
    if sorted(selections) != [1, 2, 3]:
        if len(selections) != len(set(selections)):
            return "duplicate_runner"
        return "incomplete_runners"
    if group["best_back_price"].isna().any() or (group["best_back_price"] <= 0).any():
        return "invalid_odds"
    winner_count = int((group["result"].astype(str).str.upper() == "WIN").sum())
    if winner_count != 1:
        return "ambiguous_winner"
    if group["market_start_time"].isna().any():
        return "invalid_date"
    return None


def _closing_odds(group: pd.DataFrame | None) -> dict[str, float | None]:
    odds = {"HOME": None, "DRAW": None, "AWAY": None}
    if group is None:
        return odds
    valid = group.dropna(subset=["selection_id", "best_back_price"])
    for row in valid.itertuples(index=False):
        try:
            selection = normalize_result(int(row.selection_id))
        except ValueError:
            continue
        if odds[selection] is None and float(row.best_back_price) > 0:
            odds[selection] = float(row.best_back_price)
    return odds


def _date_string(value: object) -> str | None:
    if pd.isna(value):
        return None
    return pd.Timestamp(value).isoformat()

