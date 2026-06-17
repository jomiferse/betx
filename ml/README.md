## Current status

BetX ML is currently paused.

ML-001, ML-002 and ML-002.1 showed that the current odds-only,
Elo, rolling form, historical goals and HistGradientBoosting models
do not consistently outperform the market baseline.

The ML output must not be used for paper trading or real-money betting.

Production integration: disabled.
Runtime dependency: none.
Development status: paused.

Decision date: 2026-06-18.

Dataset used: `backtest/football-data/normalized/opening-closing.csv` with raw Football-Data CSVs from `backtest/football-data/raw`.

Dataset identifier: SHA-256 `0d6c42b5c7029fe5200712fa0de8f9f426691faacf0758162328f65b0e1ad473`.

Markets evaluated: `10,728` valid markets, `0` raw unmatched rows.

Split identifier: chronological split, no shuffle, random seed `42`, validation size `0.15`, test size `0.20`; rows `train=6,972`, `validation=1,611`, `test=2,145`.

Primary reproducible result:

```text
PAUSE_CURRENT_ML_FEATURES
NO_FEATURE_FAMILY_BEATS_ODDS_ONLY
MODEL_DOES_NOT_BEAT_MARKET_BASELINE
```

ML is paused because the tested feature families did not provide a stable predictive improvement over odds-only or the market baseline, and the economics did not justify integration into BetX paper trading, Telegram, readiness, or real-money flows.

Development should resume only when materially new pre-match data is available, such as temporal odds movement, Betfair snapshots, BACK/LAY spread, liquidity, bookmaker dispersion, lineups, injuries and suspensions, xG or advanced match statistics, or a new temporal period not used for the pause decision.

Small decision artifacts are retained under `data/ml/runs/ml-002-1-ablation-check/`. Full predictions, trained models, and complete run directories are local artifacts and should not be committed.

# BetX ML-001 Offline Football Probability Baseline

This package trains an offline football probability baseline for BetX. It reads BetX normalized runner-level historical CSVs, pivots each valid market into one match row, trains a chronological logistic regression model, compares it with normalized market probabilities, and exports reproducible artifacts.

It does not integrate with the Java runtime, Telegram, paper trading, the readiness gate, or real betting.

## Install

From the repository root:

```bash
cd ml
uv sync
```

## Train

```bash
uv run python -m betx_ml train \
  --input ../backtest/football-data/normalized/opening-closing.csv \
  --output-dir ../data/ml/runs \
  --test-size 0.20 \
  --validation-size 0.15 \
  --random-seed 42
```

## Train ML-002

ML-002 keeps the normalized `opening-closing.csv` as the market spine and joins raw Football-Data match facts only to build historical team features. Raw joins are strict by default: if a market cannot be matched to required raw fields (`Date`, `HomeTeam`, `AwayTeam`, `FTHG`, `FTAG`, `FTR`), training stops instead of filling hidden defaults.

```bash
uv run python -m betx_ml train \
  --input ../backtest/football-data/normalized/opening-closing.csv \
  --raw-dir ../backtest/football-data/raw \
  --output-dir ../data/ml/runs \
  --test-size 0.20 \
  --validation-size 0.15 \
  --random-seed 42
```

The run compares market baseline, logistic regression odds-only, logistic regression with `team_strength_v1`, and HistGradientBoosting with `team_strength_v1`. Model selection uses validation metrics, then the final test set is evaluated once.

## Run ML-002.1 Ablation

ML-002.1 compares feature families without adding new algorithms or looking at test for selection:

```bash
uv run python -m betx_ml ablation \
  --input ../backtest/football-data/normalized/opening-closing.csv \
  --raw-dir ../backtest/football-data/raw \
  --output-dir ../data/ml/runs \
  --run-id ml-002-1-ablation
```

The ablation command trains LogisticRegression on `odds_only`, `odds_elo`, `odds_form`, `odds_goals`, `odds_elo_form`, and `odds_team_strength_all`. It trains HistGradientBoosting on every feature family except `odds_only`. Edge sensitivity is validation-only; test receives the frozen threshold selected from validation for each combination.

Ablation runs create:

```text
ablation_results.csv
ablation_summary.json
feature_importance.csv
model_comparison.csv
fold_comparison.csv
edge_diagnostics_validation.csv
segment_diagnostics_ablation.csv
```

The summary classifies each feature family as `IMPROVES_OVER_ODDS_ONLY`, `NEUTRAL`, `DEGRADES`, or `UNSTABLE`. ROI is diagnostic only and is not used as the primary reason to continue.

Each run creates:

```text
metrics.json
predictions.csv
bets.csv
walk_forward_results.csv
validation_edge_sensitivity.csv
segment_diagnostics.csv
raw_join_diagnostics.csv
feature_manifest.json
model_metadata.json
dataset_quality.json
model.joblib
```

## Predict

```bash
uv run python -m betx_ml predict \
  --model ../data/ml/runs/<run-id>/model.joblib \
  --input ../backtest/football-data/normalized/opening-closing.csv \
  --output ../data/ml/predictions.csv
```

## Dataset Rules

The loader uses a stable market key:

```text
exchange + "|" + market_id
```

Predictive input uses only `odds_source=opening-bookmaker`. Each valid market must have exactly one HOME, DRAW, and AWAY runner using BetX football-data selection IDs:

```text
1 = HOME
2 = DRAW
3 = AWAY
```

Each market must also have exactly one `WIN` runner. Incomplete, duplicated, ambiguous, invalid-odds, or invalid-date markets are excluded and reported in `dataset_quality.json`.

`closing-average` odds are never used as model features. They are joined only after prediction for CLV reporting.

## Features

The first baseline is intentionally odds-only:

- HOME, DRAW, and AWAY opening odds
- raw implied probabilities
- normalized implied probabilities after removing overround
- market overround
- league
- month and season-month index

Team form and rolling team statistics are intentionally deferred until they have strict temporal leakage tests.

## Splits

Splits are chronological by `market_start_time`:

```text
train -> validation -> test
```

There is no shuffle. The test set is final reporting only and is not used for model selection or hyperparameter tuning.

## Betting Economics

Value betting uses fixed stake and at most one bet per market. Execution slippage matches the Java `PROFIT_HAIRCUT` model:

```text
adjusted_odds = 1 + ((opening_odds - 1) * (1 - slippage_rate))
expected_value = predicted_probability * adjusted_odds - 1
```

Commission is applied only to positive gross PnL:

```text
gross_pnl = stake * (adjusted_odds - 1) for wins
gross_pnl = -stake for losses
commission = max(gross_pnl, 0) * commission_rate
net_pnl = gross_pnl - commission
```

## CLV Convention

BACK CLV is reported only when closing odds exist:

```text
back_clv_ratio = execution_odds / closing_odds
back_clv_pct = (back_clv_ratio - 1) * 100
implied_probability_change = (1 / closing_odds) - (1 / execution_odds)
```

For BACK bets, `back_clv_ratio > 1.0` means the execution odds beat the closing odds. Missing closing odds are exported as blank values and summarized as unavailable, never as zero.

## ML-002 Team Strength v1

The first team-strength feature set is intentionally small:

```text
home_elo_pre
away_elo_pre
elo_diff
home_points_last_5
away_points_last_5
home_points_last_10
away_points_last_10
home_draw_rate_last_10
away_draw_rate_last_10
home_goal_diff_last_5
away_goal_diff_last_5
home_matches_available
away_matches_available
home_days_since_last_match
away_days_since_last_match
home_inactivity_decay_applied
away_inactivity_decay_applied
```

Features are emitted before updating team state and grouped by calendar day, so matches on the same day cannot see each other’s results. Elo carries between seasons with shrinkage toward 1500, rolling form resets by season, and long inactivity decays Elo toward 1500 before feature emission.

Edge sensitivity is validation-only across `1%`, `2%`, `3%`, `5%`, `7.5%`, and `10%`. The test set receives exactly one frozen threshold selected from validation.

## Prediction CSV Contract

`predictions.csv` contains:

```text
date
league
home_team
away_team
actual_result
home_odds
draw_odds
away_odds
market_home_probability
market_draw_probability
market_away_probability
model_home_probability
model_draw_probability
model_away_probability
predicted_result
split
```
