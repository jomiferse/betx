from pathlib import Path

import pandas as pd

from betx_ml.dataset import load_markets, normalize_result


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


def write_history(tmp_path: Path, rows: list[dict[str, object]]) -> Path:
    path = tmp_path / "history.csv"
    pd.DataFrame(rows, columns=HEADER).to_csv(path, index=False)
    return path


def row(
    market_id: str,
    selection_id: int,
    runner: str,
    odds: float,
    result: str,
    *,
    odds_source: str = "opening-bookmaker",
    start: str = "2024-08-10T15:00:00Z",
    league: str = "E0",
    season: str = "2024/25",
) -> dict[str, object]:
    return {
        "observed_at": "2024-08-10T07:00:00Z" if odds_source == "opening-bookmaker" else "2024-08-10T13:00:00Z",
        "exchange": "football-data",
        "market_id": market_id,
        "market_name": "Match Odds",
        "event_name": "Alpha v Beta",
        "competition_name": league,
        "season": season,
        "odds_source": odds_source,
        "market_start_time": start,
        "selection_id": selection_id,
        "runner_name": runner,
        "best_back_price": odds,
        "best_lay_price": round(odds * 1.04, 2),
        "spread": 0.04,
        "liquidity": 1000,
        "result": result,
    }


def valid_market(market_id: str = "m1") -> list[dict[str, object]]:
    return [
        row(market_id, 1, "Alpha", 2.0, "LOSE"),
        row(market_id, 2, "Draw", 3.4, "WIN"),
        row(market_id, 3, "Beta", 3.8, "LOSE"),
        row(market_id, 1, "Alpha", 1.9, "LOSE", odds_source="closing-average"),
        row(market_id, 2, "Draw", 3.2, "WIN", odds_source="closing-average"),
        row(market_id, 3, "Beta", 4.0, "LOSE", odds_source="closing-average"),
    ]


def test_normalizes_results_to_home_draw_away():
    assert normalize_result(1) == "HOME"
    assert normalize_result(2) == "DRAW"
    assert normalize_result(3) == "AWAY"


def test_loads_valid_opening_market_and_joins_closing_odds(tmp_path: Path):
    result = load_markets(write_history(tmp_path, valid_market()))

    assert len(result.markets) == 1
    market = result.markets.iloc[0]
    assert market["market_key"] == "football-data|m1"
    assert market["actual_result"] == "DRAW"
    assert market["home_odds"] == 2.0
    assert market["draw_odds"] == 3.4
    assert market["away_odds"] == 3.8
    assert market["closing_home_odds"] == 1.9
    assert market["closing_draw_odds"] == 3.2
    assert market["closing_away_odds"] == 4.0
    assert result.quality.markets_read == 1
    assert result.quality.valid_markets == 1
    assert result.quality.discarded_markets == 0


def test_excludes_incomplete_duplicate_ambiguous_and_invalid_markets(tmp_path: Path):
    rows = []
    rows.extend(valid_market("valid"))
    rows.extend([row("missing", 1, "Home", 2.0, "WIN"), row("missing", 2, "Draw", 3.1, "LOSE")])
    rows.extend(valid_market("duplicate")[:3])
    rows.append(row("duplicate", 1, "Home duplicate", 2.1, "LOSE"))
    rows.extend([row("ambiguous", 1, "Home", 2.0, "WIN"), row("ambiguous", 2, "Draw", 3.1, "WIN"), row("ambiguous", 3, "Away", 4.0, "LOSE")])
    rows.extend([row("badodds", 1, "Home", 0.0, "WIN"), row("badodds", 2, "Draw", 3.1, "LOSE"), row("badodds", 3, "Away", 4.0, "LOSE")])

    result = load_markets(write_history(tmp_path, rows))

    assert result.markets["market_id"].tolist() == ["valid"]
    assert result.quality.markets_read == 5
    assert result.quality.valid_markets == 1
    assert result.quality.discarded_markets == 4
    assert result.quality.discard_reasons == {
        "ambiguous_winner": 1,
        "duplicate_runner": 1,
        "incomplete_runners": 1,
        "invalid_odds": 1,
    }

