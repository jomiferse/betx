# BetX
Terminal-first betting signals engine built with Java, Spring Boot and Picocli.
Safe by default: dry-run mode, Telegram test alerts, no real betting execution.

## Usage
Requirements: Java 21+ and Maven 3.9+.
Build: `mvn test && mvn package`.
Init: `java -jar target/betx.jar init`.
Start watcher: `java -jar target/betx.jar start`.
Run one cycle: `java -jar target/betx.jar start --once`.
Telegram: `java -jar target/betx.jar telegram connect|status|test`.

## Market Data
`start` is read-only and never places real bets. It polls configured exchanges every
`market_data.poll_interval_seconds`, stores normalized runner snapshots in SQLite at
`storage.path`, compares each runner with its previous snapshot, and prints event
analysis recommendations: `BET DRY-RUN`, `WATCH`, or `NO BET`.

The first analyzer version is technical: it filters test markets, missing odds,
low liquidity, wide spreads, odds outside the configured range, and only emits
`BET DRY-RUN` when market quality is acceptable and recent odds or liquidity
movement is favorable. It is not yet a sports prediction model.

Default `market_data` configuration:

```yaml
market_data:
  poll_interval_seconds: 60
  scan_all_markets: true
  max_markets: 0
  betfair_event_batch_size: 50
  event_type_ids:
    - "1"
  market_type_codes:
    - MATCH_ODDS
```

With `scan_all_markets: true`, BetX discovers all active Betfair football
events for the configured market types and scans them in event batches. Set
`scan_all_markets: false` and a positive `max_markets` for a small smoke test.
Telegram alerts are sent only for `BET DRY-RUN` recommendations.
