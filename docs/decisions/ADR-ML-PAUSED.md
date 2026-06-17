# ADR: Pause BetX Machine Learning Development

Date: 2026-06-18

## Context

BetX has an offline Python ML laboratory under `ml/`. It was created to test whether historical football market data and pre-match features could improve HOME/DRAW/AWAY probability estimates beyond the market baseline.

The Java runtime remains the product system. It handles market polling, paper trading, Telegram, readiness checks, and real order safety. ML outputs are not part of that runtime.

## Experiments

- ML-001: odds-only LogisticRegression baseline.
- ML-002: strict raw Football-Data joins, Elo, rolling form, historical goals, and HistGradientBoosting.
- ML-002.1: feature-family ablation across odds-only, Elo, form, goals, combined team strength, LogisticRegression, and HistGradientBoosting.

The reproducible decision run used `backtest/football-data/normalized/opening-closing.csv` plus raw Football-Data CSVs, covering `10,728` valid markets with `0` raw unmatched rows. Dataset SHA-256: `0d6c42b5c7029fe5200712fa0de8f9f426691faacf0758162328f65b0e1ad473`.

## Results

```text
PAUSE_CURRENT_ML_FEATURES
NO_FEATURE_FAMILY_BEATS_ODDS_ONLY
MODEL_DOES_NOT_BEAT_MARKET_BASELINE
```

The ablation selected `logistic_regression` with `odds_only` as the best validation configuration. Elo, rolling form, historical goals, combined team strength, and HistGradientBoosting did not provide stable improvement.

## Decision

Keep the `ml/` module as an offline experimental laboratory, but pause ML development and disable any production integration.

ML outputs must not be used for paper trading, Telegram, readiness gates, Java runtime decisions, or real-money betting.

## Consequences

- Python ML tests remain independent and can be run with `cd ml && uv run pytest`.
- Java builds and runtime must not train models or load ML artifacts.
- Only small decision artifacts should be versioned; trained models, full predictions, and complete run directories remain local artifacts.
- Product and operations work should continue separately from ML research.

## Reopening Criteria

Reopen the ML line only when materially new pre-match data is available, such as temporal odds movement, Betfair snapshots, BACK/LAY spread, liquidity, bookmaker dispersion, lineups, injuries and suspensions, xG or advanced match statistics, or a genuinely new temporal period not used for this decision.
