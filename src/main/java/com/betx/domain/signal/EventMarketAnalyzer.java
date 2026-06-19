package com.betx.domain.signal;

import com.betx.domain.config.RiskConfig;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Technical market analyzer for dry-run event recommendations. */
public class EventMarketAnalyzer {
    private static final BigDecimal MAX_RELATIVE_SPREAD = BigDecimal.valueOf(0.08);
    private static final BigDecimal MIN_BACK_ODDS = BigDecimal.valueOf(1.5);
    private static final BigDecimal MAX_BACK_ODDS = BigDecimal.valueOf(6.0);
    private static final BigDecimal DEFAULT_FAVORABLE_BACK_DROP_PERCENT = BigDecimal.valueOf(-1.0);
    private static final BigDecimal FAVORABLE_LIQUIDITY_RISE_PERCENT = BigDecimal.valueOf(2.0);
    private static final BigDecimal LOW_VOLATILITY_PERCENT = BigDecimal.valueOf(15.0);
    private static final BigDecimal STABLE_DRAW_MOVEMENT_PERCENT = BigDecimal.valueOf(1.0);
    private static final int HOME_RUNNER_SCORE_BOOST = 25;
    private static final int STABLE_DRAW_SCORE_BOOST = 25;
    private static final int AWAY_VALUE_SCORE_BOOST = 25;
    private static final int BET_SCORE_THRESHOLD = 70;

    private final BigDecimal favorableBackDropPercent;

    public EventMarketAnalyzer() {
        this(DEFAULT_FAVORABLE_BACK_DROP_PERCENT);
    }

    public EventMarketAnalyzer(BigDecimal favorableBackDropPercent) {
        this.favorableBackDropPercent = favorableBackDropPercent == null
            ? DEFAULT_FAVORABLE_BACK_DROP_PERCENT
            : favorableBackDropPercent;
    }

    public RunnerAnalysis analyze(
        MarketSnapshot snapshot,
        Optional<MarketSnapshot> previousSnapshot,
        StrategyConfig strategyConfig,
        RiskConfig riskConfig
    ) {
        return analyze(
            snapshot,
            previousSnapshot.map(previous -> List.of(new ObservedMarketSnapshot(Instant.EPOCH, previous))).orElseGet(List::of),
            strategyConfig,
            riskConfig
        );
    }

    public RunnerAnalysis analyze(
        MarketSnapshot snapshot,
        List<ObservedMarketSnapshot> recentSnapshots,
        StrategyConfig strategyConfig,
        RiskConfig riskConfig
    ) {
        if (isTestMarket(snapshot)) {
            return rejected(snapshot, strategyConfig.name(), "test_market");
        }
        if (snapshot.bestBackPrice() == null || snapshot.bestLayPrice() == null) {
            return rejected(snapshot, strategyConfig.name(), "missing_back_or_lay_price");
        }
        if (snapshot.liquidity().compareTo(strategyConfig.minLiquidity()) < 0) {
            return rejected(snapshot, strategyConfig.name(), "liquidity_below_minimum");
        }
        if (snapshot.spread() == null || snapshot.spread().compareTo(MAX_RELATIVE_SPREAD) > 0) {
            return rejected(snapshot, strategyConfig.name(), "spread_above_threshold");
        }
        if (snapshot.bestBackPrice().compareTo(MIN_BACK_ODDS) < 0 || snapshot.bestBackPrice().compareTo(MAX_BACK_ODDS) > 0) {
            return rejected(snapshot, strategyConfig.name(), "odds_out_of_range");
        }
        RunnerProfile runnerProfile = RunnerProfile.from(snapshot);

        List<MarketSnapshot> history = recentSnapshots == null
            ? List.of()
            : recentSnapshots.stream().map(ObservedMarketSnapshot::snapshot).toList();
        if (runnerProfile == RunnerProfile.DRAW && history.isEmpty()) {
            return rejected(snapshot, strategyConfig.name(), "draw_runner_not_supported");
        }
        if (history.isEmpty()) {
            return RunnerAnalysis.from(
                snapshot,
                RecommendationType.WATCH,
                "valid_market_waiting_for_movement",
                SignalScore.fromValue(35, List.of("Base market quality is acceptable")),
                strategyConfig.name()
            );
        }

        MarketMovementFeatures features = features(snapshot, history);
        boolean stableDrawProfile = isStableDrawProfile(snapshot, features, runnerProfile);
        if (runnerProfile == RunnerProfile.DRAW && !stableDrawProfile) {
            return rejected(snapshot, strategyConfig.name(), "draw_runner_not_supported");
        }
        boolean awayValueProfile = isAwayValueProfile(snapshot, features, runnerProfile);
        if (runnerProfile == RunnerProfile.AWAY && !awayValueProfile) {
            return rejected(snapshot, strategyConfig.name(), "away_runner_value_profile_missing");
        }
        SignalScore score = score(snapshot, history, features, runnerProfile, stableDrawProfile, awayValueProfile);
        RecommendationType recommendation = score.value() >= BET_SCORE_THRESHOLD ? RecommendationType.BET : RecommendationType.WATCH;
        return RunnerAnalysis.from(
            snapshot,
            recommendation,
            reason(recommendation, features, score, runnerProfile, stableDrawProfile, awayValueProfile),
            score,
            strategyConfig.name()
        );
    }

    public boolean isTestMarket(MarketSnapshot snapshot) {
        return containsTest(snapshot.marketName()) || containsTest(snapshot.eventName()) || containsTest(snapshot.competitionName());
    }

    private boolean containsTest(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("test");
    }

    private RunnerAnalysis rejected(MarketSnapshot snapshot, String strategyName, String reason) {
        return RunnerAnalysis.from(snapshot, RecommendationType.NO_BET, reason, SignalScore.zero(reason), strategyName);
    }

    private MarketMovementFeatures features(MarketSnapshot current, List<MarketSnapshot> history) {
        MarketSnapshot previous = history.getFirst();
        BigDecimal oddsDelta = percentageDelta(previous.bestBackPrice(), current.bestBackPrice());
        BigDecimal liquidityDelta = percentageDelta(previous.liquidity(), current.liquidity());
        return new MarketMovementFeatures(
            oddsDelta,
            liquidityDelta,
            persistenceCycles(current, history),
            volatilityPercent(current, history),
            history.size()
        );
    }

    private SignalScore score(
        MarketSnapshot current,
        List<MarketSnapshot> history,
        MarketMovementFeatures features,
        RunnerProfile runnerProfile,
        boolean stableDrawProfile,
        boolean awayValueProfile
    ) {
        List<String> reasons = new ArrayList<>();
        int value = 35;
        reasons.add("Base market quality is acceptable");

        MarketSnapshot previous = history.getFirst();
        if (features.oddsDeltaPercent() != null && features.oddsDeltaPercent().compareTo(favorableBackDropPercent) <= 0) {
            value += 25;
            reasons.add("Odds moved from " + numeric(previous.bestBackPrice()) + " -> " + numeric(current.bestBackPrice()));
        } else if (features.oddsDeltaPercent() != null && features.oddsDeltaPercent().signum() < 0) {
            value += 10;
            reasons.add("Odds moved slightly from " + numeric(previous.bestBackPrice()) + " -> " + numeric(current.bestBackPrice()));
        }

        if (features.liquidityDeltaPercent() != null && features.liquidityDeltaPercent().compareTo(FAVORABLE_LIQUIDITY_RISE_PERCENT) >= 0) {
            value += 20;
            reasons.add("Liquidity increased " + signedPercent(features.liquidityDeltaPercent()));
        } else if (features.liquidityDeltaPercent() != null && features.liquidityDeltaPercent().signum() > 0) {
            value += 5;
            reasons.add("Liquidity increased slightly " + signedPercent(features.liquidityDeltaPercent()));
        }

        if (features.favorablePersistenceCycles() >= 3) {
            value += 10;
            reasons.add("Movement persisted for 3 cycles");
        } else if (features.favorablePersistenceCycles() == 2) {
            value += 5;
            reasons.add("Movement persisted for 2 cycles");
        } else if (features.favorablePersistenceCycles() == 1) {
            value += 5;
        }

        boolean lowVolatility = features.volatilityPercent() == null || features.volatilityPercent().compareTo(LOW_VOLATILITY_PERCENT) <= 0;
        if (lowVolatility) {
            value += 10;
            reasons.add("Volatility is low");
        } else {
            value -= 25;
            reasons.add("Volatility is high");
        }

        if (lowVolatility && standsOutFromBaseline(current, history)) {
            value += 10;
            reasons.add("Movement stands out versus recent baseline");
        }
        if (runnerProfile == RunnerProfile.HOME) {
            value += HOME_RUNNER_SCORE_BOOST;
            reasons.add("Home runner profile");
        }
        if (stableDrawProfile) {
            value += STABLE_DRAW_SCORE_BOOST;
            reasons.add("Stable draw profile");
        }
        if (awayValueProfile) {
            value += AWAY_VALUE_SCORE_BOOST;
            reasons.add("Away value profile");
        }

        return SignalScore.fromValue(value, reasons);
    }

    private String reason(
        RecommendationType recommendation,
        MarketMovementFeatures features,
        SignalScore score,
        RunnerProfile runnerProfile,
        boolean stableDrawProfile,
        boolean awayValueProfile
    ) {
        List<String> tokens = new ArrayList<>(List.of("liquidity_ok", "spread_ok", "odds_range_ok"));
        if (features.oddsDeltaPercent() != null && features.oddsDeltaPercent().compareTo(favorableBackDropPercent) <= 0) {
            tokens.add("favorable_odds_movement");
        }
        if (features.liquidityDeltaPercent() != null && features.liquidityDeltaPercent().compareTo(FAVORABLE_LIQUIDITY_RISE_PERCENT) >= 0) {
            tokens.add("favorable_liquidity_movement");
        }
        if (features.favorablePersistenceCycles() >= 2) {
            tokens.add("movement_persisted");
        }
        if (score.reasons().contains("Volatility is low")) {
            tokens.add("low_volatility");
        }
        if (runnerProfile == RunnerProfile.HOME) {
            tokens.add("home_runner_profile");
        }
        if (stableDrawProfile) {
            tokens.add("stable_draw_profile");
        }
        if (awayValueProfile) {
            tokens.add("away_value_profile");
        }
        if (recommendation == RecommendationType.BET) {
            tokens.add("dry_run_only");
        } else {
            tokens.add("score_below_threshold");
        }
        return String.join(", ", tokens);
    }

    private int persistenceCycles(MarketSnapshot current, List<MarketSnapshot> history) {
        List<MarketSnapshot> sequence = new ArrayList<>();
        sequence.add(current);
        sequence.addAll(history.stream().limit(3).toList());
        int cycles = 0;
        for (int index = 0; index < sequence.size() - 1; index++) {
            MarketSnapshot newer = sequence.get(index);
            MarketSnapshot older = sequence.get(index + 1);
            BigDecimal oddsDelta = percentageDelta(older.bestBackPrice(), newer.bestBackPrice());
            BigDecimal liquidityDelta = percentageDelta(older.liquidity(), newer.liquidity());
            boolean favorableOdds = oddsDelta != null && oddsDelta.signum() < 0;
            boolean favorableLiquidity = liquidityDelta != null && liquidityDelta.signum() > 0;
            if (!favorableOdds && !favorableLiquidity) {
                break;
            }
            cycles++;
        }
        return cycles;
    }

    private BigDecimal volatilityPercent(MarketSnapshot current, List<MarketSnapshot> history) {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(current.bestBackPrice());
        history.stream().limit(4).map(MarketSnapshot::bestBackPrice).forEach(prices::add);
        prices = prices.stream().filter(price -> price != null && price.compareTo(BigDecimal.ZERO) > 0).toList();
        if (prices.size() < 3) {
            return BigDecimal.ZERO;
        }
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal average = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size()), 10, RoundingMode.HALF_UP);
        if (average.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return max.subtract(min)
            .divide(average, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(8, RoundingMode.HALF_UP);
    }

    private boolean standsOutFromBaseline(MarketSnapshot current, List<MarketSnapshot> history) {
        List<BigDecimal> prices = history.stream()
            .map(MarketSnapshot::bestBackPrice)
            .filter(price -> price != null && price.compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (prices.size() < 2 || current.bestBackPrice() == null) {
            return false;
        }
        BigDecimal average = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size()), 10, RoundingMode.HALF_UP);
        BigDecimal delta = percentageDelta(average, current.bestBackPrice());
        return delta != null && delta.compareTo(favorableBackDropPercent) <= 0;
    }

    private boolean isStableDrawProfile(
        MarketSnapshot snapshot,
        MarketMovementFeatures features,
        RunnerProfile runnerProfile
    ) {
        if (runnerProfile != RunnerProfile.DRAW || features.oddsDeltaPercent() == null) {
            return false;
        }
        return snapshot.bestBackPrice().compareTo(new BigDecimal("3.00")) > 0
            && snapshot.bestBackPrice().compareTo(MAX_BACK_ODDS) <= 0
            && features.oddsDeltaPercent().abs().compareTo(STABLE_DRAW_MOVEMENT_PERCENT) <= 0;
    }

    private boolean isAwayValueProfile(
        MarketSnapshot snapshot,
        MarketMovementFeatures features,
        RunnerProfile runnerProfile
    ) {
        if (runnerProfile != RunnerProfile.AWAY || features.oddsDeltaPercent() == null) {
            return false;
        }
        BigDecimal odds = snapshot.bestBackPrice();
        BigDecimal movement = features.oddsDeltaPercent();
        if (odds.compareTo(new BigDecimal("2.00")) <= 0) {
            return isModerateDrop(movement) || isStrongDrift(movement);
        }
        if (odds.compareTo(new BigDecimal("3.00")) <= 0) {
            return movement.abs().compareTo(STABLE_DRAW_MOVEMENT_PERCENT) <= 0;
        }
        return isModerateDrop(movement) || isStrongDrift(movement);
    }

    private boolean isModerateDrop(BigDecimal movement) {
        return movement.compareTo(favorableBackDropPercent) <= 0
            && movement.compareTo(new BigDecimal("-10.00")) > 0;
    }

    private boolean isStrongDrift(BigDecimal movement) {
        return movement.compareTo(new BigDecimal("5.00")) > 0;
    }

    private String signedPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        return (rounded.signum() > 0 ? "+" : "") + rounded.toPlainString() + "%";
    }

    private String numeric(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(8, RoundingMode.HALF_UP);
    }

    private enum RunnerProfile {
        HOME,
        DRAW,
        AWAY,
        UNKNOWN;

        private static RunnerProfile from(MarketSnapshot snapshot) {
            if (!isMatchOdds(snapshot)) {
                return UNKNOWN;
            }
            if (snapshot.runnerType() == RunnerType.HOME) {
                return HOME;
            }
            if (snapshot.runnerType() == RunnerType.DRAW) {
                return DRAW;
            }
            if (snapshot.runnerType() == RunnerType.AWAY) {
                return AWAY;
            }
            if (snapshot.runnerName() == null || snapshot.eventName() == null) {
                return UNKNOWN;
            }
            if ("draw".equalsIgnoreCase(snapshot.runnerName())) {
                return DRAW;
            }
            if ("the draw".equalsIgnoreCase(snapshot.runnerName().strip())) {
                return DRAW;
            }
            String[] teams = snapshot.eventName().split("\\s+v\\s+", 2);
            if (teams.length != 2) {
                return UNKNOWN;
            }
            if (snapshot.runnerName().equalsIgnoreCase(teams[0].strip())) {
                return HOME;
            }
            if (snapshot.runnerName().equalsIgnoreCase(teams[1].strip())) {
                return AWAY;
            }
            return UNKNOWN;
        }

        private static boolean isMatchOdds(MarketSnapshot snapshot) {
            return snapshot.marketName() != null && "match odds".equalsIgnoreCase(snapshot.marketName().strip());
        }
    }
}
