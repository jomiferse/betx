# Repository Guidelines

## Project Structure & Module Organization

BetX is a Java 21, Spring Boot, Picocli CLI application. Production code lives in `src/main/java/com/betx`, grouped by responsibility:

- `domain/`: immutable records and domain rules (`signal`, `config`, `betfair`, `order`).
- `application/`: use-case services and outbound ports.
- `adapter/`: infrastructure adapters for YAML config, Betfair, and Telegram.
- `cli/`: Picocli commands.
- `startup/` and `common/`: rendering and shared exceptions.

Tests mirror the same package layout in `src/test/java`. Runtime assets are local directories `data/` and `models/`, created by `init`. Build artifacts are generated under `target/`.

## Machine Learning Status

The Python module under `ml/` is retained only as an offline experimental laboratory. ML-001, ML-002, and ML-002.1 are paused after the reproducible decision:

```text
PAUSE_CURRENT_ML_FEATURES
NO_FEATURE_FAMILY_BEATS_ODDS_ONLY
MODEL_DOES_NOT_BEAT_MARKET_BASELINE
```

Do not integrate ML output with Java runtime decisions, paper trading, Telegram, readiness gates, or real-money betting. Do not make Maven train models or load `model.joblib`/`predictions.csv`. Python ML tests remain independent and may be run with `cd ml && uv run pytest`.

ML development should resume only when materially new pre-match data is available, such as temporal odds movement, Betfair snapshots, BACK/LAY spread, liquidity, bookmaker dispersion, lineups, injuries/suspensions, xG or advanced statistics, or a new temporal period not used for the pause decision.

## Build, Test, and Development Commands

- `mvn test`: runs the JUnit 5 test suite.
- `mvn package`: compiles, tests, and builds `target/betx.jar`.
- `java -jar target/betx.jar init`: creates starter `betx.yml`, `data/`, and `models/`.
- `java -jar target/betx.jar start --config betx.yml`: starts BetX using the configured exchanges and auto-betting settings.
- `java -jar target/betx.jar start --config betx.yml --once`: runs one polling cycle and exits.
- `java -jar target/betx.jar paper-trade --config betx.yml`: records read-only prospective paper recommendations.
- `java -jar target/betx.jar paper-readiness --config betx.yml`: inspects persisted paper evidence before unattended real betting.
- `java -jar target/betx.jar betfair test --config betx.yml`: validates Betfair credentials.
- `java -jar target/betx.jar telegram status --config betx.yml`: checks Telegram connection state.

## Coding Style & Naming Conventions

Use Java records for immutable data carriers and constructor injection for services/components. Keep package boundaries clear: domain code should not depend on adapters or CLI classes. Prefer explicit imports over fully qualified class names in code. Use PascalCase for classes/records, camelCase for methods and fields, and UPPER_SNAKE_CASE for constants.

No formatter plugin is currently configured; follow the existing 4-space indentation and compact, readable methods.

## Testing Guidelines

Use JUnit 5 and AssertJ from `spring-boot-starter-test`. Name tests by behavior, for example `rejectsUnsupportedAppMode` or `printsExchangeFailuresAndSignals`. Keep tests deterministic: use fakes or `MockRestServiceServer` instead of real Betfair or Telegram calls. Run `mvn test` before submitting changes; use targeted runs such as `mvn -q test -Dtest=BetfairRestGatewayTest` while iterating.

## Commit & Pull Request Guidelines

Git history is minimal and uses short imperative summaries, e.g. `Initial BetX project structure`. Keep future commits concise and action-oriented. PRs should describe the behavior changed, list verification commands run, and note any config or CLI compatibility impact. Include sample terminal output for CLI-facing changes.

## Security & Configuration Tips

Do not commit real tokens, passwords, session data, or local `betx.yml` secrets. The working `betx.yml` may contain local credentials when the user explicitly requests file-based configuration, but generated templates and documentation must use placeholders. Never print API keys, Betfair credentials, Telegram tokens, chat IDs, or session tokens in logs, CLI output, tests, or summaries.

## Architecture Rules

Follow a hexagonal architecture style:

- Domain must remain framework-free.
- Domain objects must not depend on Spring, Picocli, Jackson, RestClient, YAML, Telegram, or Betfair SDK/API classes.
- Application services orchestrate use cases and depend only on domain objects and ports.
- Outbound ports live under `application/port/out`.
- Adapters implement outbound ports and contain infrastructure-specific logic.
- CLI commands should be thin: parse input, call application services, and render results.

Do not bypass ports by calling adapters directly from application or domain code.

## Configuration Guidelines

Configuration is loaded from `betx.yml` and environment variables. The local project may use `betx.yml` as the single source of runtime configuration when requested by the user.

Do not hardcode secrets, tokens, chat IDs, Betfair credentials, stake limits, or strategy thresholds in Java code.

When adding new config fields:

- Update the default generated `betx.yml`.
- Add validation for required or unsafe values.
- Keep backwards compatibility where reasonable.
- Document the new option in `README.md`.

Current runtime configuration conventions:

- There is no global live/dry-run mode. Betting behavior is controlled per exchange.
- Betfair auto-betting settings live under the Betfair exchange configuration, including `enabled`, `request_confirmation`, stake limits, odds limits, and expiry.
- External match intelligence is configured under `intelligence`. OpenRouter is the supported provider and may use Grok models.
- If OpenRouter is enabled and reachable, its assessment is advisory when `request_confirmation` is enabled.
- If `request_confirmation` is disabled, OpenRouter approval is mandatory before any automatic bet. Missing, failed, `WATCH`, or `REJECT` intelligence must block automatic betting without breaking the rest of the BetX polling process.
- If `paper.readiness_gate.enabled` is true and `request_confirmation` is disabled, persisted paper evidence must be `READY` before any automatic real bet. Telegram-confirmed betting may still show readiness as advisory.

## CLI User Experience

CLI output should be clear, concise, and useful for non-technical users.

Commands should:

- Explain what happened.
- Show next steps when setup is incomplete.
- Avoid exposing secrets.
- Use consistent success, warning, and error formatting.
- Prefer actionable messages over raw exception text.

When a real bet is not placed, CLI and Telegram output must make that clear. Bet confirmation alerts should separate market confidence from betting/context confidence and avoid presenting `WATCH` or `REJECT` intelligence as a recommended entry.

## Betting Safety

Real order execution must not be changed casually.

Any real betting path must include risk limits, validation, logging, explicit configuration, and either user confirmation or mandatory external intelligence approval.

- Do not enable auto-betting by default in generated templates.
- Do not place a real bet unless the configured exchange auto-betting rules allow it.
- Respect `request_confirmation`; when enabled, no bet should be placed until the user confirms.
- Respect the paper readiness gate for unattended automatic betting; a non-`READY` enabled gate must block automatic execution without blocking Telegram confirmation.
- Respect stake, odds, and expiry limits before creating or executing an order.
- Do not store Betfair session tokens in logs or generated files.
- OpenRouter or any external intelligence provider must never stop the normal BetX polling process; failures should degrade to `UNAVAILABLE`/non-approval behavior according to confirmation settings.
- Automatic real order execution must use the per-exchange execution queue/reservation flow so multiple orders in the same cycle cannot all spend the same balance snapshot.
- Persist live-order balance audit fields (`available_balance`, `effective_available_balance`, `reserved_balance`, and `balance_snapshot_at`) whenever a real automatic order is accepted or blocked by execution safety.

## Testing Expectations

New behavior should include tests when practical.

Prioritize tests for:

- Config loading and validation.
- CLI command behavior.
- Betfair and Telegram adapters.
- Domain rules and signal generation.
- Error handling paths.

External integrations must be tested with fakes, stubs, or `MockRestServiceServer`. Do not require real credentials or network access for the test suite.

## Dependency Guidelines

Avoid adding new dependencies unless they clearly reduce complexity or are required for a specific feature.

Before adding a dependency:

- Prefer standard Java or existing Spring Boot functionality.
- Check whether the project already has an equivalent library.
- Keep the dependency aligned with Java 21 and the current Spring Boot version.
- Explain the reason in the PR description.

## Multi-Exchange Design

BetX is designed as a multi-exchange betting/trading tool.

Betfair is the first supported exchange, but the core application and domain logic must remain exchange-agnostic so that future exchanges can be added without rewriting use cases or domain rules.

When adding exchange-related features:

- Do not hardcode Betfair-specific concepts into domain or application services unless they are truly universal.
- Keep Betfair-specific request/response models inside the Betfair adapter package.
- Use generic domain names such as `Exchange`, `Market`, `Runner`, `Selection`, `Price`, `Order`, and `Position` where possible.
- Use outbound ports for exchange operations, for example market discovery, odds retrieval, account checks, and order placement.
- Implement each exchange as a separate adapter.
- Avoid leaking exchange-specific IDs, enums, errors, or authentication details into the domain unless wrapped in generic BetX concepts.
- If an exchange requires unique behavior, isolate it behind adapter mapping or exchange-specific configuration.
- New exchanges should be added by implementing existing ports or introducing generic ports, not by modifying existing use cases around one provider.
