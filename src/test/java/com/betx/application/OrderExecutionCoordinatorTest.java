package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.config.ExecutionQueueConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrderExecutionCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-06-17T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reservesBalanceSequentiallyWithinExchange() {
        OrderExecutionCoordinator coordinator = new OrderExecutionCoordinator(CLOCK);
        ExecutionQueueConfig config = ExecutionQueueConfig.defaults();

        OrderExecutionCoordinator.OrderExecutionReservation first = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(17.7),
            BigDecimal.ONE,
            NOW
        );
        OrderExecutionCoordinator.OrderExecutionReservation second = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(17.7),
            BigDecimal.ONE,
            NOW
        );

        assertThat(first.allowed()).isTrue();
        assertThat(first.effectiveAvailableBalance()).isEqualByComparingTo("17.7");
        assertThat(first.reservedBalance()).isEqualByComparingTo("0");
        assertThat(second.allowed()).isTrue();
        assertThat(second.effectiveAvailableBalance()).isEqualByComparingTo("16.7");
        assertThat(second.reservedBalance()).isEqualByComparingTo("1");
    }

    @Test
    void keepsReservationsIsolatedByExchange() {
        OrderExecutionCoordinator coordinator = new OrderExecutionCoordinator(CLOCK);
        ExecutionQueueConfig config = ExecutionQueueConfig.defaults();

        coordinator.reserve(config, "betfair", BigDecimal.valueOf(10), BigDecimal.ONE, NOW);
        OrderExecutionCoordinator.OrderExecutionReservation smarkets = coordinator.reserve(
            config,
            "smarkets",
            BigDecimal.valueOf(20),
            BigDecimal.ONE,
            NOW
        );

        assertThat(smarkets.allowed()).isTrue();
        assertThat(smarkets.effectiveAvailableBalance()).isEqualByComparingTo("20");
        assertThat(smarkets.reservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void releasesReservationWhenExecutionIsRejected() {
        OrderExecutionCoordinator coordinator = new OrderExecutionCoordinator(CLOCK);
        ExecutionQueueConfig config = ExecutionQueueConfig.defaults();

        OrderExecutionCoordinator.OrderExecutionReservation first = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            NOW
        );
        coordinator.complete(first, false);
        OrderExecutionCoordinator.OrderExecutionReservation second = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(2),
            BigDecimal.ONE,
            NOW
        );

        assertThat(second.allowed()).isTrue();
        assertThat(second.effectiveAvailableBalance()).isEqualByComparingTo("2");
        assertThat(second.reservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void blocksExpiredQueuedOrder() {
        OrderExecutionCoordinator coordinator = new OrderExecutionCoordinator(CLOCK);
        ExecutionQueueConfig config = new ExecutionQueueConfig(true, 20, "10s", "5s", "3s", new BigDecimal("0.01"));

        OrderExecutionCoordinator.OrderExecutionReservation reservation = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(10),
            BigDecimal.ONE,
            NOW.minusSeconds(11)
        );

        assertThat(reservation.allowed()).isFalse();
        assertThat(reservation.blockMessage()).isEqualTo("Order queue TTL expired.");
    }

    @Test
    void blocksWhenExchangeQueueLimitIsReached() {
        OrderExecutionCoordinator coordinator = new OrderExecutionCoordinator(CLOCK);
        ExecutionQueueConfig config = new ExecutionQueueConfig(true, 1, "10s", "5s", "3s", new BigDecimal("0.01"));

        OrderExecutionCoordinator.OrderExecutionReservation first = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(10),
            BigDecimal.ONE,
            NOW
        );
        OrderExecutionCoordinator.OrderExecutionReservation second = coordinator.reserve(
            config,
            "betfair",
            BigDecimal.valueOf(10),
            BigDecimal.ONE,
            NOW
        );

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.blockMessage()).isEqualTo("Execution queue limit reached.");
    }
}
