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

Each run creates:

```text
metrics.json
predictions.csv
bets.csv
walk_forward_results.csv
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

CLV is reported only when closing odds exist:

```text
decimal_clv_ratio = closing_odds / execution_odds
implied_probability_change = (1 / closing_odds) - (1 / execution_odds)
```

Missing closing odds are exported as blank values and summarized as unavailable, never as zero.

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

