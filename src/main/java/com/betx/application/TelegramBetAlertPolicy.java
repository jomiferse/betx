package com.betx.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Limits which dry-run BET alerts should be sent to Telegram in one cycle. */
class TelegramBetAlertPolicy {
    TelegramBetAlertSelection select(List<TelegramBetAlertCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new TelegramBetAlertSelection(List.of(), List.of());
        }

        Map<String, TelegramBetAlertCandidate> bestLiquidityByMarket = bestLiquidityByMarket(candidates);
        List<TelegramBetAlertCandidate> alertsToSend = new ArrayList<>();
        List<TelegramBetAlertSkip> skippedAlerts = new ArrayList<>();
        Set<String> sentLiquidityMarkets = new java.util.HashSet<>();

        for (TelegramBetAlertCandidate candidate : candidates) {
            if (candidate.trigger() == TelegramBetAlertTrigger.ODDS_MOVEMENT) {
                alertsToSend.add(candidate);
                continue;
            }

            TelegramBetAlertCandidate bestCandidate = bestLiquidityByMarket.get(candidate.marketKey());
            if (candidate.equals(bestCandidate) && sentLiquidityMarkets.add(candidate.marketKey())) {
                alertsToSend.add(candidate);
            } else {
                skippedAlerts.add(new TelegramBetAlertSkip(candidate, "liquidity_market_limit"));
            }
        }

        return new TelegramBetAlertSelection(alertsToSend, skippedAlerts);
    }

    private Map<String, TelegramBetAlertCandidate> bestLiquidityByMarket(List<TelegramBetAlertCandidate> candidates) {
        return candidates.stream()
            .filter(candidate -> candidate.trigger() == TelegramBetAlertTrigger.LIQUIDITY_MOVEMENT)
            .collect(Collectors.toMap(
                TelegramBetAlertCandidate::marketKey,
                Function.identity(),
                this::bestLiquidityCandidate,
                LinkedHashMap::new
            ));
    }

    private TelegramBetAlertCandidate bestLiquidityCandidate(
        TelegramBetAlertCandidate left,
        TelegramBetAlertCandidate right
    ) {
        return liquidityComparator().compare(left, right) <= 0 ? left : right;
    }

    private Comparator<TelegramBetAlertCandidate> liquidityComparator() {
        return Comparator.comparing(TelegramBetAlertCandidate::isDraw)
            .thenComparing(candidate -> candidate.analysis().spread(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.analysis().liquidity(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.analysis().bestBackPrice(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(candidate -> candidate.analysis().selectionId());
    }
}
