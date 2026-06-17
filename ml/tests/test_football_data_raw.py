from pathlib import Path

import pandas as pd
import pytest

from betx_ml.football_data_raw import load_raw_matches, slug_team


def write_raw(path: Path) -> Path:
    pd.DataFrame(
        [
            {
                "Div": "E0",
                "Date": "10/08/2024",
                "Time": "15:00",
                "HomeTeam": "Alpha FC",
                "AwayTeam": "Beta",
                "FTHG": 2,
                "FTAG": 1,
                "FTR": "H",
            }
        ]
    ).to_csv(path, index=False, encoding="utf-8-sig")
    return path


def test_slug_team_matches_converter_style():
    assert slug_team("M'gladbach") == "m-gladbach"
    assert slug_team("Nott'm Forest") == "nott-m-forest"
    assert slug_team("Bayern München") == "bayern-munchen"


def test_loads_raw_matches_with_required_fields_and_join_key(tmp_path: Path):
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    write_raw(raw_dir / "2425-E0.csv")

    result = load_raw_matches(raw_dir)

    assert result.quality.rows_read == 1
    assert result.quality.valid_rows == 1
    match = result.matches.iloc[0]
    assert match["league"] == "E0"
    assert match["season"] == "2024/25"
    assert match["match_date"].isoformat() == "2024-08-10"
    assert match["home_team_key"] == "alpha-fc"
    assert match["away_team_key"] == "beta"
    assert match["fthg"] == 2
    assert match["ftag"] == 1
    assert match["ftr"] == "H"


def test_rejects_duplicate_raw_join_keys(tmp_path: Path):
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    first = pd.read_csv(write_raw(raw_dir / "2425-E0.csv"), encoding="utf-8-sig")
    pd.concat([first, first], ignore_index=True).to_csv(raw_dir / "2425-E0.csv", index=False, encoding="utf-8-sig")

    with pytest.raises(ValueError, match="Duplicate raw match keys"):
        load_raw_matches(raw_dir)
