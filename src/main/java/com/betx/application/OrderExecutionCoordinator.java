package com.betx.application;

import com.betx.domain.config.ExecutionQueueConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Coordinates fast, per-exchange real order execution reservations within a polling cycle. */
public final class OrderExecutionCoordinator {
    private final Clock clock;
    private final Map<String, ExchangeQueueState> states = new LinkedHashMap<>();

    public OrderExecutionCoordinator(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public OrderExecutionReservation reserve(
        ExecutionQueueConfig config,
        String exchange,
        BigDecimal availableBalance,
        BigDecimal stake,
        Instant queuedAt
    ) {
        ExecutionQueueConfig queue = config == null ? ExecutionQueueConfig.defaults() : config;
        Instant now = Instant.now(clock);
        ExchangeQueueState state = states.computeIfAbsent(exchange, ignored -> new ExchangeQueueState(availableBalance, now));
        if (!queue.enabled()) {
            return OrderExecutionReservation.accepted(
                exchange,
                availableBalance,
                availableBalance,
                BigDecimal.ZERO,
                now,
                stake
            );
        }
        if (state.queuedThisCycle >= queue.maxPendingPerExchange()) {
            return OrderExecutionReservation.blocked(
                exchange,
                state.availableBalance,
                state.effectiveAvailableBalance(),
                state.reservedBalance,
                state.snapshotAt,
                stake,
                "Execution queue limit reached."
            );
        }
        state.queuedThisCycle++;
        if (queuedAt != null && Duration.between(queuedAt, now).compareTo(queue.orderTtl()) > 0) {
            return OrderExecutionReservation.blocked(
                exchange,
                state.availableBalance,
                state.effectiveAvailableBalance(),
                state.reservedBalance,
                state.snapshotAt,
                stake,
                "Order queue TTL expired."
            );
        }
        if (state.availableBalance == null || state.availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return OrderExecutionReservation.blocked(
                exchange,
                state.availableBalance,
                null,
                state.reservedBalance,
                state.snapshotAt,
                stake,
                "Balance unavailable. Bet blocked for safety."
            );
        }
        BigDecimal effectiveAvailableBalance = state.effectiveAvailableBalance();
        if (effectiveAvailableBalance.compareTo(queue.minEffectiveBalance()) < 0 || effectiveAvailableBalance.compareTo(stake) < 0) {
            return OrderExecutionReservation.blocked(
                exchange,
                state.availableBalance,
                effectiveAvailableBalance,
                state.reservedBalance,
                state.snapshotAt,
                stake,
                "Effective balance unavailable. Bet blocked for safety."
            );
        }
        BigDecimal reservedBefore = state.reservedBalance;
        state.reservedBalance = state.reservedBalance.add(stake);
        return OrderExecutionReservation.accepted(
            exchange,
            state.availableBalance,
            effectiveAvailableBalance,
            reservedBefore,
            state.snapshotAt,
            stake
        );
    }

    public void complete(OrderExecutionReservation reservation, boolean accepted) {
        if (reservation == null || !reservation.allowed() || accepted) {
            return;
        }
        ExchangeQueueState state = states.get(reservation.exchange());
        if (state == null) {
            return;
        }
        state.reservedBalance = state.reservedBalance.subtract(reservation.stake()).max(BigDecimal.ZERO);
    }

    public record OrderExecutionReservation(
        String exchange,
        boolean allowed,
        BigDecimal availableBalance,
        BigDecimal effectiveAvailableBalance,
        BigDecimal reservedBalance,
        Instant balanceSnapshotAt,
        BigDecimal stake,
        String blockMessage
    ) {
        private static OrderExecutionReservation accepted(
            String exchange,
            BigDecimal availableBalance,
            BigDecimal effectiveAvailableBalance,
            BigDecimal reservedBalance,
            Instant balanceSnapshotAt,
            BigDecimal stake
        ) {
            return new OrderExecutionReservation(
                exchange,
                true,
                availableBalance,
                effectiveAvailableBalance,
                reservedBalance,
                balanceSnapshotAt,
                stake,
                null
            );
        }

        private static OrderExecutionReservation blocked(
            String exchange,
            BigDecimal availableBalance,
            BigDecimal effectiveAvailableBalance,
            BigDecimal reservedBalance,
            Instant balanceSnapshotAt,
            BigDecimal stake,
            String blockMessage
        ) {
            return new OrderExecutionReservation(
                exchange,
                false,
                availableBalance,
                effectiveAvailableBalance,
                reservedBalance,
                balanceSnapshotAt,
                stake,
                blockMessage
            );
        }
    }

    private static final class ExchangeQueueState {
        private final BigDecimal availableBalance;
        private final Instant snapshotAt;
        private BigDecimal reservedBalance = BigDecimal.ZERO;
        private int queuedThisCycle;

        private ExchangeQueueState(BigDecimal availableBalance, Instant snapshotAt) {
            this.availableBalance = availableBalance;
            this.snapshotAt = snapshotAt;
        }

        private BigDecimal effectiveAvailableBalance() {
            if (availableBalance == null) {
                return null;
            }
            return availableBalance.subtract(reservedBalance);
        }
    }
}
