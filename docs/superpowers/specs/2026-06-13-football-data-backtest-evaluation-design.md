# Football-Data Backtest Evaluation Design

## Goal

Improve BetX strategy evaluation by making the existing football-data.co.uk backtest explain where the current `value-football` strategy performs well or poorly. The change should help tune the strategy with evidence before changing live betting behavior.

## Context

BetX already supports this path:

1. Convert a football-data.co.uk match CSV into BetX normalized backtest history.
2. Replay the normalized rows through `RunBacktestService`.
3. Place one simulated BACK trade on the first qualifying `BET` signal per runner.
4. Print aggregate performance, drawdown, and top/bottom trades.

Football-Data provides Bet365 opening and closing match odds plus final outcomes. It does not provide Betfair exchange liquidity, traded volume, lay-book depth, or intra-match order-book movement. The converter currently creates two synthetic observations per runner and writes fixed liquidity and estimated lay prices. Evaluation must therefore focus on pre-match odds movement and settled profitability, not exact exchange microstructure.

## Approach

Add a strategy-evaluation layer to the application backtest result. Keep the existing `backtest` command compatible and append compact diagnostic sections after the current summary.

The implementation will report performance by:

- odds band at simulated entry;
- runner type: home, draw, away, or unknown;
- competition;
- signal confidence label;
- opening-to-entry odds movement bucket.

Each segment includes trades, wins, losses, strike rate, staked, profit/loss, ROI, and max drawdown. The formatter shows at most five rows for each segment type, ranked by trade count descending and then ROI descending, so terminal output remains readable.

## Data Model

Introduce immutable application records:

- `BacktestEvaluation`: container for grouped segment lists.
- `BacktestSegment`: one bucket of trades and computed metrics.
- `BacktestSegmentType`: enum for `ODDS_BAND`, `RUNNER_TYPE`, `COMPETITION`, `CONFIDENCE`, and `ODDS_MOVEMENT`.

Keep these in `application` because they summarize a use case result and do not need framework or adapter dependencies.

`BacktestTrade` currently lacks the fields needed for grouping by confidence and movement. Extend it conservatively with:

- `confidenceLabel`;
- `oddsMovementPercent`;
- `runnerType`.

The values are derived when the simulated trade is created. Existing call sites that create `BacktestTrade` in tests or command fixtures must continue to compile by using a compact constructor overload that defaults the new fields to unknown values.

## Derivation Rules

Odds band:

- `<1.50`
- `1.50-2.00`
- `2.01-3.00`
- `3.01-6.00`
- `>6.00`

Runner type:

- selection id `1` means `HOME`;
- selection id `2` means `DRAW`;
- selection id `3` means `AWAY`;
- otherwise `UNKNOWN`.

This matches football-data converter output and remains harmless for generic normalized CSVs.

Odds movement:

- `steam <= -10%`
- `drop -10% to -3%`
- `drop -3% to -1%`
- `stable -1% to +1%`
- `drift +1% to +5%`
- `drift > +5%`
- `unknown`

Movement should use the most recent previous observation for that runner. With football-data converted rows, that means opening-to-closing movement for the closing observation. If a trade is created without prior history, movement is `unknown`.

Confidence:

- use `RunnerAnalysis.score().confidenceLabel()`;
- fall back to `Unknown confidence` if unavailable.

Competition:

- use `BacktestInputRow.competitionName()`;
- fall back to `unknown`.

## CLI Output

Keep current lines:

- `Backtest complete ...`
- `Performance ...`
- top trades;
- bottom trades.

Append:

```text
Strategy evaluation
By odds band
SEGMENT | odds_band | 1.50-2.00 | trades=...
By runner type
SEGMENT | runner_type | HOME | trades=...
By odds movement
SEGMENT | odds_movement | drop -10% to -3% | trades=...
```

Competition output uses the same five-row display limit as other segment types. The first implementation uses no minimum trade-count filter, so small local sample files remain transparent during experimentation.

## Error Handling

Backtest validation remains unchanged. Missing optional evaluation values should become explicit `unknown`/`UNKNOWN` buckets rather than failing the backtest. Football-data conversion failures should remain in the converter.

## Testing

Add tests for:

- grouping trades into odds bands, runner types, confidence labels, competitions, and movement buckets;
- segment metrics including ROI and max drawdown;
- `RunBacktestService` attaching confidence, runner type, and movement to simulated trades;
- `BacktestResultFormatter` printing the strategy-evaluation section;
- backwards-compatible no-trade output.

Run `mvn test` before completion.

## Out of Scope

- Changing live betting thresholds.
- Adding an optimizer or parameter sweep.
- Downloading football-data.co.uk files automatically.
- Treating bookmaker odds backtests as proof of Betfair order-book execution quality.
