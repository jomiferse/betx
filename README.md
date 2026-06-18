# BetX

BetX is a betting signals engine for football markets with a CLI for operations and a local web interface for day-to-day use.

It reads exchange market data, stores snapshots locally, detects useful price and liquidity movement, and sends actionable Telegram alerts. It is safe by default: auto-betting is disabled unless explicitly configured per exchange.

Current version: `0.7.0`

## What It Does

- Scans configured Betfair football markets.
- Prints `BET`, `WATCH`, and `NO BET` recommendations in the terminal.
- Stores market snapshots in SQLite for change detection.
- Scores each runner from `0-100` using recent odds, liquidity, persistence, and volatility.
- Sends filtered Telegram alerts for actionable `BET` signals.
- Supports Betfair auto-betting with optional Telegram confirmation.
- Provides a local web interface at `/interface` for status, activation, pause, and recent activity.
- Provides paper readiness checks for internal strategy and release validation.
- Runs restart-safe prospective paper trading for `value-football-draw-only`.
- Writes separated local audit logs for CLI, paper-trade, and Telegram messages.

## Requirements

- Java 21+
- Maven 3.9+
- Betfair API credentials for real market data
- A Telegram bot token if you want alerts

Node.js is not required to run the packaged application. Maven installs an isolated Node/npm toolchain under `frontend/.node` only while building the React interface.

## Quick Start

```bash
mvn package
java -jar target/betx.jar init
java -jar target/betx.jar interface --config betx.yml
```

The generated `betx.yml` starts with Betfair disabled. Add your exchange credentials and enable the exchange before expecting live market data.

The interface command starts the local Spring Boot app and opens:

```text
http://localhost:8080/interface/
```

## Common Commands

```bash
# Build and run tests
mvn test
mvn package

# Create starter config and local folders
java -jar target/betx.jar init

# Run one market-data cycle
java -jar target/betx.jar start --config betx.yml --once

# Run continuously
java -jar target/betx.jar start --config betx.yml

# Start the local web interface
java -jar target/betx.jar interface --config betx.yml

# Start the local web interface without opening a browser
java -jar target/betx.jar interface --config betx.yml --no-browser

# Replay historical normalized CSV data
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv

# Inspect the paused offline ML laboratory
cd ml
uv sync
uv run pytest
cd ..

# Convert Football-Data CSV to BetX history format
java -jar target/betx.jar backtest convert-football-data --input backtest/SP1.csv --output backtest/history.csv

# Telegram setup and checks
java -jar target/betx.jar telegram connect --config betx.yml
java -jar target/betx.jar telegram status --config betx.yml
java -jar target/betx.jar telegram test --config betx.yml

# Betfair checks
java -jar target/betx.jar betfair test --config betx.yml
java -jar target/betx.jar betfair markets --config betx.yml
```

The offline ML laboratory lives under `ml/` and is currently paused. Its outputs are retained for research diagnostics only and must not affect live betting, paper trading, Telegram, readiness gates, or Java runtime decisions. See `ml/README.md` for the paused status and reopening criteria.

## Local Web Interface

The commercial interface is intentionally small and product-oriented. It is available from the `interface` command and served by the same JAR as the backend:

```bash
java -jar target/betx.jar interface --config betx.yml
```

By default, BetX opens the browser automatically. Use `--no-browser` when running headless, and `--port` to choose another local port:

```bash
java -jar target/betx.jar interface --config betx.yml --port 18080 --no-browser
```

The current interface shows:

- BetX status: active, paused, or needs attention.
- Available balance when the exchange account can provide it.
- Manual confirmation state.
- Recent activity.
- Actions to activate or pause BetX.

The frontend is a React + TypeScript + Vite app under `frontend/`. Maven builds it, runs its tests, and copies `frontend/dist` into `target/classes/static/interface`, so the final JAR serves it from `/interface`.

The interface API is versioned under:

```text
/api/v1/interface/*
```

Internal tooling remains in the CLI. Backtesting, paper trading, readiness checks, ML experiments, low-level logs, and diagnostics are not exposed in the commercial interface.

The execution mode is controlled internally by `betx.yml` exchange configuration. The interface must not expose a PAPER/LIVE selector. If the user needs mode context, use product language such as `Modo simulacion` or `Apuestas reales`, without allowing mode changes from the first interface.

Frontend-only development commands are for contributors:

```bash
cd frontend
.node/node/npm test -- --run
.node/node/npm run build
```

## Configuration

The main config file is `betx.yml`.

Important defaults:

```yaml
exchanges:
  - name: betfair
    enabled: false
    betfair:
      auto_betting:
        enabled: false
        request_confirmation: true
        max_stake: 5
        max_daily_loss: 25
        max_open_positions: 3

storage:
  path: ./data/betx.db
  cleanup_market_snapshots_enabled: true
  market_snapshot_retention_hours: 48

execution:
  queue:
    enabled: true
    max_pending_per_exchange: 20
    order_ttl: 10s
    stale_balance_ttl: 5s
    revalidate_odds_after: 3s
    min_effective_balance: 0.01
```

Telegram credentials can be stored in `betx.yml` or supplied with environment variables:

```bash
export TELEGRAM_BOT_TOKEN=...
export TELEGRAM_CHAT_ID=...
```

## Telegram Alerts And Confirmations

Signal alerts are filtered to reduce noise:

- Odds-movement `BET` signals are sent when the confidence score reaches the signal threshold.
- Liquidity-only `BET` signals are limited to one alert per market per cycle.
- The first continuous `start` cycle suppresses Telegram alerts as a warmup.

Alerts show the score, confidence label, and human-readable reasons. Fixture names are formatted for readability, for example `Australia vs Switzerland` instead of Betfair's raw `Australia v Switzerland`.

Example:

```text
BETX SIGNAL
SIGNAL ONLY

Market movement detected
Score: 85/100 High confidence

Australia vs Switzerland
Bet: Draw @ 3.90

Why this signal:
- Odds moved from 3.95 -> 3.90
- Movement persisted for 3 cycles
- Volatility is low
```

When Betfair `auto_betting.enabled` and `request_confirmation` are both true, `BET` signals use Telegram confirmation:

1. BetX sends a card with `Yes` and `No`.
2. `Yes` shows available balance and allowed stake buttons.
3. Selecting a stake sends the live order to the exchange.
4. `No` or `Cancel` closes the pending intent.

When `auto_betting.enabled` is true and `request_confirmation` is false, BetX sends orders automatically, capped by the Betfair auto-betting limits. Accepted real orders send a Telegram order message with event, selection, odds, stake, balance when available, and Betfair bet id.

For unattended continuous mode, the first `start` cycle is treated as a warmup and skips automatic orders. This avoids executing stale startup signals while still allowing subsequent cycles to trade normally under the configured limits.

Before any live order is sent, BetX checks Betfair for real exposure and available balance. `max_open_positions` counts open Betfair positions, including manual bets placed outside BetX. `max_daily_loss` is the realized/liquidated loss for the current UTC day from Betfair settlements. If Betfair exposure or balance cannot be read, BetX blocks the live order for safety. BetX reuses per-cycle exposure reads and stores at most one blocked intent when the configured open-position capacity is full, reducing database noise and unnecessary Betfair login/request pressure.

Automatic real orders use a fast in-memory execution queue per exchange. Signal analysis remains immediate, but live order placement is serialized for each exchange so multiple bets in the same cycle do not all decide against the same balance snapshot. `bet_intents.available_balance` is the exchange balance snapshot before local reservations; `effective_available_balance` subtracts already reserved stakes in that cycle, and `reserved_balance` records the amount reserved before the current order. If effective balance cannot cover the next stake, BetX stores a blocked intent and does not call the exchange.

Telegram defaults to product-oriented key events rather than a raw stream of every signal:

```yaml
telegram:
  alerts:
    mode: key_events
    signal_dedupe_ttl: 30m
```

Use `all_signals` only while diagnosing strategy behavior. Runtime API failures are reported as warnings and should not stop the main polling loop.

Transient API failures use conservative resilience defaults:

```yaml
resilience:
  betfair:
    failure_threshold: 3
    cooldown: 5m
  telegram:
    failure_threshold: 3
    cooldown: 5m
  openrouter:
    failure_threshold: 3
    cooldown: 5m
```

Real order execution queue defaults are conservative:

```yaml
execution:
  queue:
    enabled: true
    max_pending_per_exchange: 20
    order_ttl: 10s
    stale_balance_ttl: 5s
    revalidate_odds_after: 3s
    min_effective_balance: 0.01
```

## Local Logs

When `app.log_level: info`, BetX mirrors terminal and alert output into daily local audit files:

- CLI commands: `logs/cli/messages_DDMMYYYY.txt`
- Paper trading: `logs/paper-trade/messages_DDMMYYYY.txt`
- Sent Telegram messages: `logs/telegram/messages_DDMMYYYY.txt`

These files are runtime artifacts and are ignored by Git. They are useful for auditing exactly what BetX printed or sent without mixing paper-trade diagnostics with normal CLI output.

## OpenRouter Match Intelligence

BetX can use OpenRouter with a Grok model and web search to review current news and real-time match context after a technical `BET` signal is found:

```yaml
intelligence:
  enabled: true
  provider: openrouter
  model: x-ai/grok-4.3
  api_key: sk-or-v1-...
  api_key_env: OPENROUTER_API_KEY
  timeout_seconds: 20
  min_confidence: 70
  auto_betting_policy: strict_approve
```

`api_key` is read directly from `betx.yml`. If `api_key` is blank, BetX falls back to the environment variable named by `api_key_env`. Treat any config file containing `api_key` as a secret and do not commit it.

Behavior:

- If `request_confirmation: false`, OpenRouter acts as the unattended auto-betting gate whenever `intelligence.enabled: true`.
- The default `auto_betting_policy: strict_approve` preserves the safest behavior: only `APPROVE` can proceed to an automatic bet; `WATCH`, `REJECT`, missing assessments, and `UNAVAILABLE` block the bet.
- `auto_betting_policy: block_only_on_reject` is a more permissive unattended policy: `APPROVE` and `WATCH` can proceed, while `REJECT`, missing assessments, and `UNAVAILABLE` still block the bet. Use this only when you intentionally accept that uncertain external context may still allow an automatic order.
- If `request_confirmation: true`, OpenRouter is advisory. The Telegram confirmation card includes the recommendation when OpenRouter returns one, but BetX still sends the confirmation even if OpenRouter rejects, watches, or is unavailable.
- OpenRouter failures never stop the scan cycle. They are treated as unavailable intelligence and logged.

Unattended intelligence behavior is controlled by the combination of provider/model quality, `min_confidence`, `auto_betting_policy`, and `request_confirmation`. A stronger model can improve the assessment quality, but the configured policy decides whether `WATCH` is allowed to become an automatic order.

## Safety

BetX does not enable auto-betting by default.

To place real bets on Betfair, enable auto-betting for that exchange:

```yaml
exchanges:
  - name: betfair
    enabled: true
    betfair:
      auto_betting:
        enabled: true
        request_confirmation: true
        max_stake: 5
        max_daily_loss: 25
        max_open_positions: 3
```

Use `request_confirmation: true` to require Telegram approval before any order is sent. Use `request_confirmation: false` only when you want fully automatic orders.

BetX rechecks Betfair exposure immediately before executing either a confirmed Telegram stake or an automatic order. Local Telegram intents are kept for confirmations, cooldown, and traceability, but they are not the source of truth for real exposure.

## Snapshot Retention

BetX stores raw `market_snapshots` only as short-lived signal input. By default, each signal cycle deletes snapshots for markets whose `market_start_time` is older than 48 hours. When a BetX order is reconciled as `SETTLED`, snapshots for that exchange market are also removed if snapshot cleanup is enabled.

```yaml
storage:
  cleanup_market_snapshots_enabled: true
  market_snapshot_retention_hours: 48
```

Longer-term model or result analysis should use compact signal/decision/result summaries rather than keeping every raw market tick indefinitely.

## Current Strategy

The first strategy is technical, not predictive.

It filters out poor markets, missing prices, low liquidity, wide spreads, and odds outside the configured range. After those quality gates pass, BetX compares the current runner with recent local SQLite snapshots and builds an explainable score from:

- favorable back-odds movement;
- relative liquidity movement;
- movement persistence across up to three cycles;
- recent volatility;
- whether the move stands out versus the local runner baseline.

A `BET` recommendation requires a score of at least `70/100`. Lower scores remain `WATCH`, and failed quality gates stay `NO BET`.

## Historical Backtesting

BetX can replay a normalized historical CSV through a research comparison engine without calling live exchanges, Telegram, OpenRouter, or order execution:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv
```

The CSV must include these columns:

```text
observed_at,exchange,market_id,market_name,event_name,competition_name,season,odds_source,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result
```

`season` and `odds_source` are optional for legacy files and inferred as `YYYY/YY` plus `unknown` when missing. New Football-Data imports write them explicitly. `result` must be `WIN` or `LOSE`, and result fields are used only for settlement after recommendations are created. BetX compares these strategies under the same historical replay model:

- `value-football`
- `value-football-draw-only`
- `favorite`
- `home-favorite`
- `away-underdog`
- `draw`
- `random`

The report ranks strategies automatically and prints trades, ROI, max drawdown, and strike rate. League breakdown rows show the same metrics per strategy and competition. The report header also prints the pricing mode, effective commission rate, odds source, and dataset capability:

- `SINGLE_PRICE`: one bookmaker price per runner. Analyzer-backed strategies report explicit incompatibility/rejection diagnostics when movement history is unavailable.
- `OPENING_CLOSING`: paired bookmaker observations where opening odds are history and closing odds are the tradable observation.
- `EXCHANGE_SNAPSHOTS`: exchange-style repeated runner snapshots.

`random` is deterministic by default using seed `42`. Override it when comparing repeatable alternative samples:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --random-seed 7
```

Export the ranked strategy and league comparison to CSV. The comparison CSV includes the effective commission and odds-slippage assumptions in every row:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --export-csv backtest/comparison.csv
```

Export the focused `value-football-draw-only` cumulative equity curve to CSV:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --export-equity-csv backtest/draw-only-equity.csv
```

Export focused draw-only paper-trade rows with recommendation, execution, closing-line, result and PnL fields. Historical `opening-closing` backtests mark CLV as `NOT_AVAILABLE` because the recommendation is generated from the closing observation; ROI and execution loss remain valid, but CLV is not used as a signal-quality gate in that mode:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --export-paper-csv backtest/draw-only-paper.csv
```

For prospective paper trading against upcoming configured exchange markets, run the read-only paper recorder. It evaluates only `value-football-draw-only`, stores lifecycle records in the configured SQLite database, saves current snapshots after analysis for the next scan, and does not place orders, send Telegram bet confirmations, mutate live betting state, or call external intelligence. The command is restart-safe and idempotent for the same exchange, market, and selection.

```bash
java -jar target/betx.jar paper-trade --config betx.yml
```

To run it autonomously, enable continuous mode either from the CLI or from `betx.yml`:

```bash
java -jar target/betx.jar paper-trade \
  --config betx.yml \
  --continuous \
  --poll-interval 60s
```

```yaml
paper:
  continuous: true
  poll_interval: 60s
  closing_capture_minutes_before_start: 2
  settlement_poll_interval: 5m
  readiness_gate:
    enabled: true
    minimum_settled_trades: 100
    required_evidence_status: CANDIDATE_EDGE
    minimum_executable_roi: 0.01
    minimum_median_clv: 0.00
    rolling_window_size: 100
    minimum_rolling_roi: 0.00
    block_on_execution_failure: true
```

Continuous paper mode reuses the same SQLite database across restarts. It never clears paper trades or market snapshots at startup, so the second and later cycles can analyze against snapshots saved by earlier cycles. Press Ctrl+C to request graceful shutdown; BetX finishes the current paper-trading cycle before exiting.

When `app.log_level: info`, continuous paper output is also written to `logs/paper-trade/messages_DDMMYYYY.txt`.

Paper records move through `RECOMMENDED`, `EXECUTED`, `CLOSED`, `SETTLED`, or `EXECUTION_FAILED`. Recommendation and execution use the live snapshot available at recommendation time. Closing odds are captured later near market start from a separate snapshot and are never exposed to recommendation analysis. Settlement is applied only after a settlement source reports an outcome. CSV export remains available for audit:

```bash
java -jar target/betx.jar paper-trade --config betx.yml --output data/paper-trades.csv
```

Paper mode prints operational diagnostics: markets scanned, recommendations generated, duplicates skipped, execution failures, missing closing prices, unsettled markets, settled trades, CLV count, validation status, league breakdown, and rolling 100/250/500 trade windows. Detailed `paper_signal_evaluations` rows are compact by default: accepted paper entries and strategically relevant draw evaluations are stored, while structural rejections such as non-draw runners are summarized in diagnostics instead of filling the database. Prospective CLV gates use only settled trades with independently captured closing odds. Fewer than 300 settled trades reports `INSUFFICIENT_SAMPLE`; otherwise non-positive median CLV reports `WEAK_EVIDENCE`, positive theoretical ROI with non-positive executable ROI reports `EXECUTION_FAILURE`, and positive median CLV plus positive executable ROI reports `CANDIDATE_EDGE`.

Paper readiness can be inspected separately for internal strategy and release validation:

```bash
java -jar target/betx.jar paper-readiness --config betx.yml
```

`paper-readiness` is not part of the normal `start` runtime path. End users do not need to run paper validation before using `start`, and paper readiness status is not shown in Telegram alerts. Automatic betting during `start` is controlled by the exchange auto-betting configuration and the normal runtime safety checks, including stake limits, daily loss limits, available balance, execution queue reservations, and open position limits.

```yaml
storage:
  paper_evaluations:
    detail_retention_days: 7
    rejection_sample_rate: 0.0
```

Bookmaker reports default to zero commission. Exchange snapshot reports default to a conservative Betfair-style commission baseline of `0.05`. Override either assumption when needed:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --commission-rate 0.02
java -jar target/betx.jar paper-trade --config betx.yml --commission-rate 0.02
```

Commission is applied once per market to positive gross market PnL only. Losing and break-even markets pay zero commission. Reports include gross PnL, commission, net PnL, net ROI, gross/net max drawdown, market selection buckets, runner/league/odds-band breakdowns, independent season validation, fixed development/validation/test period diagnostics, leakage diagnostics, analyzer rejection diagnostics, and statistical uncertainty for `value-football-draw-only`.

Execution price degradation can be simulated with `--odds-slippage-rate`. The analyzer still sees the original historical odds; slippage is applied only after recommendations are generated. The default model is `PROFIT_HAIRCUT`, which uses `adjustedOdds = 1 + ((originalOdds - 1) * (1 - slippageRate))`. `TOTAL_ODDS_MULTIPLIER` applies the rate to the whole decimal price. The default rate is `0`. Reports and CSV exports print the selected slippage model, and reports always include `value-football-draw-only` stress scenarios for `0`, `0.01`, `0.02`, and `0.03`:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --odds-slippage-rate 0.02 --slippage-model PROFIT_HAIRCUT
```

The focused draw-only section also reports league-season metrics, average odds, longest losing streak, opening-to-closing movement buckets, CLV availability status, conservative validation gates, rolling windows for 100/250/500 trades, and a cumulative equity curve suitable for external charting. Historical validation uses executable ROI and 3% slippage resilience; prospective validation uses median CLV and positive CLV percentage only when CLV status is `VALID_PROSPECTIVE`.

Robustness diagnostics for league ROI, walk-forward validation, and movement threshold sensitivity remain available:

```bash
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv --robustness
```

Football-Data CSV files can be converted into this normalized format:

```bash
mkdir -p backtest
curl -L -o backtest/SP1.csv https://www.football-data.co.uk/mmz4281/2526/SP1.csv
java -jar target/betx.jar backtest convert-football-data --input backtest/SP1.csv --output backtest/history.csv --odds-source opening-closing
java -jar target/betx.jar backtest --config betx.yml --input backtest/history.csv
```

Use `--odds-source opening-bookmaker` for Bet365 opening columns (`B365H/D/A`), `--odds-source closing-average` for closing average columns (`B365CH/CD/CA`), or `--odds-source opening-closing` to emit both observations for the same match. Paired mode does not fabricate additional intraday snapshots: opening rows are written before closing rows, and only closing rows are treated as tradable observations. The converter preserves league and season on every row, infers the season from the match date when `--season` is omitted, and suppresses duplicate matches when multiple input files are combined.

Football-Data does not include exchange liquidity or lay prices, so BetX writes fixed liquidity and an estimated lay price suitable for strategy replay, not exact exchange microstructure analysis. Treat the evaluation as evidence about pre-match odds and settled profitability, not proof of Betfair order-book execution quality.

## License

BetX is proprietary software. All rights are reserved. See `LICENSE`.
