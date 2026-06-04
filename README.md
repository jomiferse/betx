# BetX

BetX is a terminal-first betting signals engine for football markets.

It reads exchange market data, stores snapshots locally, detects useful price and liquidity movement, and sends actionable Telegram alerts. It is safe by default: new projects start in dry-run mode and live betting stays disabled until explicitly configured.

## What It Does

- Scans configured Betfair football markets.
- Prints `BET`, `WATCH`, and `NO BET` recommendations in the terminal.
- Stores market snapshots in SQLite for change detection.
- Sends filtered Telegram alerts for actionable `BET` signals.
- Supports Telegram button confirmation before a live bet is executed.

## Requirements

- Java 21+
- Maven 3.9+
- Betfair API credentials for real market data
- A Telegram bot token if you want alerts

## Quick Start

```bash
mvn package
java -jar target/betx.jar init
java -jar target/betx.jar start --config betx.yml --once
```

The generated `betx.yml` starts with Betfair disabled. Add your exchange credentials and enable the exchange before expecting live market data.

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

# Telegram setup and checks
java -jar target/betx.jar telegram connect --config betx.yml
java -jar target/betx.jar telegram status --config betx.yml
java -jar target/betx.jar telegram test --config betx.yml

# Betfair checks
java -jar target/betx.jar betfair test --config betx.yml
java -jar target/betx.jar betfair markets --config betx.yml
```

## Configuration

The main config file is `betx.yml`.

Important defaults:

```yaml
app:
  mode: dry-run

risk:
  max_stake: 5
  live_betting_enabled: false

storage:
  path: ./data/betx.db
```

Telegram credentials can be stored in `betx.yml` or supplied with environment variables:

```bash
export TELEGRAM_BOT_TOKEN=...
export TELEGRAM_CHAT_ID=...
```

## Telegram Alerts

Dry-run alerts are filtered to reduce noise:

- Odds-movement `BET` signals are always sent.
- Liquidity-only `BET` signals are limited to one alert per market per cycle.
- The first continuous `start` cycle suppresses Telegram alerts as a warmup.

In live mode, `BET` signals use Telegram confirmation:

1. BetX sends a card with `Yes` and `No`.
2. `Yes` shows available balance and allowed stake buttons.
3. Selecting a stake sends the live order to the exchange.
4. `No` or `Cancel` closes the pending intent.

## Safety

BetX does not enable live betting by default.

To place real bets, both conditions must be true:

```yaml
app:
  mode: live

risk:
  live_betting_enabled: true
```

Live bets still require Telegram button confirmation and are capped by `risk.max_stake`.

## Current Strategy

The first strategy is technical, not predictive.

It filters out poor markets, missing prices, low liquidity, wide spreads, and odds outside the configured range. A `BET` recommendation appears only when market quality is acceptable and the recent odds or liquidity movement is favorable.

