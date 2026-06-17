from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from datetime import date
import math

import pandas as pd


TEAM_STRENGTH_FEATURES = [
    "home_elo_pre",
    "away_elo_pre",
    "elo_diff",
    "home_points_last_5",
    "away_points_last_5",
    "home_points_last_10",
    "away_points_last_10",
    "home_draw_rate_last_10",
    "away_draw_rate_last_10",
    "home_goal_diff_last_5",
    "away_goal_diff_last_5",
    "home_matches_available",
    "away_matches_available",
    "home_days_since_last_match",
    "away_days_since_last_match",
    "home_inactivity_decay_applied",
    "away_inactivity_decay_applied",
]

BASE_ELO = 1500.0
ELO_K = 24.0
DEFAULT_DAYS_SINCE_LAST_MATCH = 14
INACTIVITY_DECAY_START_DAYS = 30
INACTIVITY_DECAY_PER_30_DAYS = 0.10
SEASON_CARRYOVER = 0.75


@dataclass
class TeamState:
    elo: float = BASE_ELO
    matches: int = 0
    last_match_date: date | None = None
    season: str | None = None
    points: deque[int] = field(default_factory=lambda: deque(maxlen=10))
    results: deque[str] = field(default_factory=lambda: deque(maxlen=10))
    goal_diff: deque[int] = field(default_factory=lambda: deque(maxlen=5))

    def prepare_for(self, season: str, match_date: date) -> tuple[float, int, int]:
        if self.season is None:
            self.season = season
        elif self.season != season:
            self.elo = BASE_ELO + SEASON_CARRYOVER * (self.elo - BASE_ELO)
            self.points.clear()
            self.results.clear()
            self.goal_diff.clear()
            self.season = season

        days_since = DEFAULT_DAYS_SINCE_LAST_MATCH
        decay_applied = 0
        if self.last_match_date is not None:
            days_since = max(0, (match_date - self.last_match_date).days)
            if days_since >= INACTIVITY_DECAY_START_DAYS:
                periods = days_since // INACTIVITY_DECAY_START_DAYS
                retention = (1.0 - INACTIVITY_DECAY_PER_30_DAYS) ** periods
                self.elo = BASE_ELO + (self.elo - BASE_ELO) * retention
                decay_applied = 1
        return self.elo, days_since, decay_applied

    def points_last(self, size: int) -> int:
        return int(sum(list(self.points)[-size:]))

    def goal_diff_last_5(self) -> int:
        return int(sum(self.goal_diff))

    def draw_rate_last_10(self) -> float:
        if not self.results:
            return 0.0
        return sum(1 for result in self.results if result == "DRAW") / len(self.results)


def add_team_strength_features(markets: pd.DataFrame) -> pd.DataFrame:
    frame = markets.copy()
    frame["date"] = pd.to_datetime(frame["date"], utc=True)
    if "match_date" not in frame.columns:
        frame["match_date"] = frame["date"].dt.date
    frame["match_date"] = pd.to_datetime(frame["match_date"]).dt.date
    frame = frame.sort_values(["match_date", "date", "market_key"]).reset_index(drop=True)
    states: dict[tuple[str, str], TeamState] = {}
    rows: list[dict[str, object]] = []

    for _, day_group in frame.groupby("match_date", sort=True):
        pending_updates = []
        for row in day_group.itertuples(index=False):
            home_key = (str(row.league), str(row.home_team_key))
            away_key = (str(row.league), str(row.away_team_key))
            home_state = states.setdefault(home_key, TeamState())
            away_state = states.setdefault(away_key, TeamState())
            home_elo, home_days, home_decay = home_state.prepare_for(str(row.season), row.match_date)
            away_elo, away_days, away_decay = away_state.prepare_for(str(row.season), row.match_date)
            item = row._asdict()
            item.update(
                {
                    "home_elo_pre": home_elo,
                    "away_elo_pre": away_elo,
                    "elo_diff": home_elo - away_elo,
                    "home_points_last_5": home_state.points_last(5),
                    "away_points_last_5": away_state.points_last(5),
                    "home_points_last_10": home_state.points_last(10),
                    "away_points_last_10": away_state.points_last(10),
                    "home_draw_rate_last_10": home_state.draw_rate_last_10(),
                    "away_draw_rate_last_10": away_state.draw_rate_last_10(),
                    "home_goal_diff_last_5": home_state.goal_diff_last_5(),
                    "away_goal_diff_last_5": away_state.goal_diff_last_5(),
                    "home_matches_available": home_state.matches,
                    "away_matches_available": away_state.matches,
                    "home_days_since_last_match": home_days,
                    "away_days_since_last_match": away_days,
                    "home_inactivity_decay_applied": home_decay,
                    "away_inactivity_decay_applied": away_decay,
                }
            )
            rows.append(item)
            pending_updates.append((row, home_key, away_key))

        for row, home_key, away_key in pending_updates:
            _update_match(states[home_key], states[away_key], row)

    result = pd.DataFrame(rows)
    return result.sort_values("date").reset_index(drop=True)


def _update_match(home: TeamState, away: TeamState, row: object) -> None:
    home_points, away_points = _points(int(row.fthg), int(row.ftag))
    expected_home = _expected(home.elo, away.elo)
    actual_home = 1.0 if home_points == 3 else 0.5 if home_points == 1 else 0.0
    home_delta = ELO_K * (actual_home - expected_home)
    home.elo += home_delta
    away.elo -= home_delta
    home.points.append(home_points)
    away.points.append(away_points)
    home.results.append(_result_for(home_points))
    away.results.append(_result_for(away_points))
    home.goal_diff.append(int(row.fthg) - int(row.ftag))
    away.goal_diff.append(int(row.ftag) - int(row.fthg))
    home.matches += 1
    away.matches += 1
    home.last_match_date = row.match_date
    away.last_match_date = row.match_date


def _points(home_goals: int, away_goals: int) -> tuple[int, int]:
    if home_goals > away_goals:
        return 3, 0
    if away_goals > home_goals:
        return 0, 3
    return 1, 1


def _result_for(points: int) -> str:
    if points == 3:
        return "WIN"
    if points == 1:
        return "DRAW"
    return "LOSS"


def _expected(elo_a: float, elo_b: float) -> float:
    return 1.0 / (1.0 + math.pow(10.0, (elo_b - elo_a) / 400.0))
