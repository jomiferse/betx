# Football-Data Backtest Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add football-data-oriented strategy evaluation segments to BetX historical backtest output.

**Architecture:** Keep the feature in the application layer. `RunBacktestService` enriches simulated trades with metadata derived from runner analysis and previous observations; `BacktestEvaluation` groups those trades into immutable segment metrics; `BacktestResultFormatter` appends compact diagnostic output.

**Tech Stack:** Java 21 records/enums, Spring Boot application services, JUnit 5, AssertJ, Maven.

---

## File Structure

- Create `src/main/java/com/betx/application/BacktestSegmentType.java`: enum for evaluation dimensions.
- Create `src/main/java/com/betx/application/BacktestRunnerType.java`: enum for HOME/DRAW/AWAY/UNKNOWN.
- Create `src/main/java/com/betx/application/BacktestSegment.java`: immutable segment metrics and factory calculation.
- Create `src/main/java/com/betx/application/BacktestEvaluation.java`: immutable grouped evaluation builder.
- Modify `src/main/java/com/betx/application/BacktestTrade.java`: add confidence, movement, runner type, and competition fields with a compatibility constructor.
- Modify `src/main/java/com/betx/application/BacktestResult.java`: include `BacktestEvaluation`.
- Modify `src/main/java/com/betx/application/RunBacktestService.java`: derive trade metadata from current row, analysis, and previous observation.
- Modify `src/main/java/com/betx/application/BacktestResultFormatter.java`: append evaluation sections.
- Modify `README.md`: document the new evaluation diagnostics.
- Test `src/test/java/com/betx/application/BacktestEvaluationTest.java`.
- Test existing `RunBacktestServiceTest` and `BacktestCommandTest`.

## Task 1: Segment Metrics Model

**Files:**
- Create: `src/main/java/com/betx/application/BacktestSegmentType.java`
- Create: `src/main/java/com/betx/application/BacktestRunnerType.java`
- Create: `src/main/java/com/betx/application/BacktestSegment.java`
- Create: `src/main/java/com/betx/application/BacktestEvaluation.java`
- Test: `src/test/java/com/betx/application/BacktestEvaluationTest.java`

- [ ] **Step 1: Write the failing segment grouping test**

```java
@Test
void groupsTradesByOddsRunnerTypeConfidenceCompetitionAndMovement() {
    BacktestEvaluation evaluation = BacktestEvaluation.from(List.of(
        trade("SP1", 1L, "Team A", "2.50", "7.50", BacktestOutcome.WIN, "High confidence", "-4.00"),
        trade("SP1", 3L, "Team B", "3.20", "-5.00", BacktestOutcome.LOSE, "Medium confidence", "2.00")
    ));

    assertThat(evaluation.segments(BacktestSegmentType.ODDS_BAND))
        .extracting(BacktestSegment::name)
        .containsExactly("2.01-3.00", "3.01-6.00");
    assertThat(evaluation.segments(BacktestSegmentType.RUNNER_TYPE))
        .extracting(BacktestSegment::name)
        .containsExactly("HOME", "AWAY");
    assertThat(evaluation.segments(BacktestSegmentType.CONFIDENCE))
        .extracting(BacktestSegment::name)
        .containsExactly("High confidence", "Medium confidence");
    assertThat(evaluation.segments(BacktestSegmentType.COMPETITION))
        .singleElement()
        .satisfies(segment -> assertThat(segment.roiPercent()).isEqualByComparingTo("25.00"));
    assertThat(evaluation.segments(BacktestSegmentType.ODDS_MOVEMENT))
        .extracting(BacktestSegment::name)
        .containsExactly("drop -10% to -3%", "drift +1% to +5%");
}
```

- [ ] **Step 2: Run the failing test**

Run: `mvn -q test -Dtest=BacktestEvaluationTest`

Expected: compilation fails because the evaluation types do not exist.

- [ ] **Step 3: Implement the minimal evaluation records**

Create enums and records that calculate grouped metrics from `BacktestTrade` lists. Use the same ROI, strike-rate, and drawdown rules as `BacktestResult`.

- [ ] **Step 4: Run the segment test**

Run: `mvn -q test -Dtest=BacktestEvaluationTest`

Expected: pass.

## Task 2: Enrich Simulated Trades

**Files:**
- Modify: `src/main/java/com/betx/application/BacktestTrade.java`
- Modify: `src/main/java/com/betx/application/RunBacktestService.java`
- Test: `src/test/java/com/betx/application/RunBacktestServiceTest.java`

- [ ] **Step 1: Write failing service assertions**

In `replaysRowsChronologicallyAndPlacesFirstSignalPerRunner`, assert the single trade has:

```java
assertThat(trade.confidenceLabel()).isEqualTo("High confidence");
assertThat(trade.runnerType()).isEqualTo(BacktestRunnerType.UNKNOWN);
assertThat(trade.oddsMovementPercent()).isEqualByComparingTo("-3.84615385");
assertThat(trade.competitionName()).isEqualTo("La Liga");
```

Add a second test using selection id `1` to assert `BacktestRunnerType.HOME`.

- [ ] **Step 2: Run the failing service test**

Run: `mvn -q test -Dtest=RunBacktestServiceTest`

Expected: compilation fails or assertions fail because trades do not expose the new fields.

- [ ] **Step 3: Extend `BacktestTrade`**

Add fields `competitionName`, `confidenceLabel`, `oddsMovementPercent`, and `runnerType`. Add a compact overload matching the old constructor and defaulting to `unknown`, `Unknown confidence`, `null`, and `UNKNOWN`.

- [ ] **Step 4: Derive metadata in `RunBacktestService`**

Pass row competition, analysis confidence label, runner type from selection id, and percent movement from the latest previous observation to `BacktestTrade`.

- [ ] **Step 5: Run the service test**

Run: `mvn -q test -Dtest=RunBacktestServiceTest`

Expected: pass.

## Task 3: Attach Evaluation To Results

**Files:**
- Modify: `src/main/java/com/betx/application/BacktestResult.java`
- Test: `src/test/java/com/betx/application/RunBacktestServiceTest.java`
- Test: `src/test/java/com/betx/application/BacktestEvaluationTest.java`

- [ ] **Step 1: Write failing result assertions**

Assert `BacktestResult.from(...).evaluation().segments(BacktestSegmentType.ODDS_BAND)` is populated for trades and empty for no-trade results.

- [ ] **Step 2: Run targeted tests**

Run: `mvn -q test -Dtest=BacktestEvaluationTest,RunBacktestServiceTest`

Expected: fails because `BacktestResult` does not expose `evaluation`.

- [ ] **Step 3: Add evaluation to `BacktestResult`**

Add a `BacktestEvaluation evaluation` record field, default null to `BacktestEvaluation.empty()`, and have `from` call `BacktestEvaluation.from(orderedTrades)`.

- [ ] **Step 4: Run targeted tests**

Run: `mvn -q test -Dtest=BacktestEvaluationTest,RunBacktestServiceTest`

Expected: pass.

## Task 4: Format Evaluation Output

**Files:**
- Modify: `src/main/java/com/betx/application/BacktestResultFormatter.java`
- Test: `src/test/java/com/betx/cli/BacktestCommandTest.java`

- [ ] **Step 1: Write failing CLI output assertions**

Extend `printsCompactBacktestSummary` to expect:

```java
.contains("Strategy evaluation")
.contains("By odds band")
.contains("SEGMENT | odds_band | 2.01-3.00 | trades=1 | wins=1 | losses=0")
.contains("By runner type")
.contains("By odds movement");
```

- [ ] **Step 2: Run the failing CLI test**

Run: `mvn -q test -Dtest=BacktestCommandTest`

Expected: assertion fails because the formatter does not print evaluation sections.

- [ ] **Step 3: Append formatter sections**

For each `BacktestSegmentType`, print a heading and up to five formatted `SEGMENT` rows. Use stable lowercase labels: `odds_band`, `runner_type`, `competition`, `confidence`, `odds_movement`.

- [ ] **Step 4: Run the CLI test**

Run: `mvn -q test -Dtest=BacktestCommandTest`

Expected: pass.

## Task 5: Documentation And Full Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README backtest section**

Add a short paragraph explaining the new strategy-evaluation breakdowns and restating the football-data limitation.

- [ ] **Step 2: Run full tests**

Run: `mvn test`

Expected: all tests pass.

- [ ] **Step 3: Review final diff**

Run: `git diff --stat` and `git diff --check`.

Expected: only planned files changed and no whitespace errors.
