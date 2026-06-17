from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
import re
import unicodedata

import pandas as pd


REQUIRED_RAW_COLUMNS = ["Div", "Date", "HomeTeam", "AwayTeam", "FTHG", "FTAG", "FTR"]


@dataclass(frozen=True)
class RawMatchQuality:
    rows_read: int
    valid_rows: int
    invalid_rows: int
    files: list[str]

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


@dataclass(frozen=True)
class RawMatchDataset:
    matches: pd.DataFrame
    quality: RawMatchQuality


def slug_team(value: str) -> str:
    normalized = unicodedata.normalize("NFD", str(value))
    ascii_text = "".join(char for char in normalized if unicodedata.category(char) != "Mn")
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_text.lower()).strip("-")
    return slug or "unknown"


def load_raw_matches(raw_dir: str | Path) -> RawMatchDataset:
    root = Path(raw_dir)
    if not root.exists():
        raise ValueError(f"Raw Football-Data directory not found: {root}")
    paths = sorted(root.glob("*.csv"))
    if not paths:
        raise ValueError(f"No raw Football-Data CSV files found in: {root}")

    frames: list[pd.DataFrame] = []
    for path in paths:
        frame = pd.read_csv(path, encoding="utf-8-sig")
        missing = sorted(set(REQUIRED_RAW_COLUMNS) - set(frame.columns))
        if missing:
            raise ValueError(f"Missing required raw columns in {path.name}: {', '.join(missing)}")
        frame = frame.copy()
        frame["source_file"] = path.name
        frames.append(frame)

    raw = pd.concat(frames, ignore_index=True)
    rows_read = len(raw)
    matches = raw.copy()
    matches["match_date"] = pd.to_datetime(matches["Date"], dayfirst=True, errors="coerce").dt.date
    matches["league"] = matches["Div"].astype(str).str.strip()
    matches["season"] = matches["match_date"].map(_season_label)
    matches["home_team"] = matches["HomeTeam"].astype(str).str.strip()
    matches["away_team"] = matches["AwayTeam"].astype(str).str.strip()
    matches["home_team_key"] = matches["home_team"].map(slug_team)
    matches["away_team_key"] = matches["away_team"].map(slug_team)
    matches["fthg"] = pd.to_numeric(matches["FTHG"], errors="coerce")
    matches["ftag"] = pd.to_numeric(matches["FTAG"], errors="coerce")
    matches["ftr"] = matches["FTR"].astype(str).str.strip().str.upper()
    required = ["match_date", "league", "season", "home_team_key", "away_team_key", "fthg", "ftag", "ftr"]
    valid = matches.dropna(subset=required).copy()
    valid = valid[valid["ftr"].isin(["H", "D", "A"])]
    valid["raw_join_key"] = _join_key(valid)
    duplicates = valid[valid["raw_join_key"].duplicated(keep=False)]
    if not duplicates.empty:
        keys = ", ".join(sorted(duplicates["raw_join_key"].astype(str).unique())[:5])
        raise ValueError(f"Duplicate raw match keys: {keys}")
    columns = [
        "raw_join_key",
        "league",
        "season",
        "match_date",
        "home_team",
        "away_team",
        "home_team_key",
        "away_team_key",
        "fthg",
        "ftag",
        "ftr",
        "source_file",
    ]
    valid = valid[columns].reset_index(drop=True)
    return RawMatchDataset(
        valid,
        RawMatchQuality(
            rows_read=rows_read,
            valid_rows=len(valid),
            invalid_rows=rows_read - len(valid),
            files=[path.name for path in paths],
        ),
    )


def raw_join_key(league: object, season: object, match_date: object, home_team: object, away_team: object) -> str:
    date = pd.Timestamp(match_date).date().isoformat()
    return "|".join([str(league), str(season), date, slug_team(str(home_team)), slug_team(str(away_team))])


def _join_key(frame: pd.DataFrame) -> pd.Series:
    return (
        frame["league"].astype(str)
        + "|"
        + frame["season"].astype(str)
        + "|"
        + frame["match_date"].map(lambda value: value.isoformat())
        + "|"
        + frame["home_team_key"].astype(str)
        + "|"
        + frame["away_team_key"].astype(str)
    )


def _season_label(value: object) -> str | None:
    if pd.isna(value):
        return None
    date = pd.Timestamp(value)
    start_year = date.year if date.month >= 7 else date.year - 1
    return f"{start_year}/{(start_year + 1) % 100:02d}"
