package com.betx.domain.exposure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Snapshot of real exchange exposure and settled daily profit/loss. */
public record ExchangeExposure(
    boolean available,
    int openPositions,
    BigDecimal currentExposure,
    BigDecimal realizedProfitLoss,
    List<ExchangeExposurePosition> positions,
    List<ExchangeSettledOrder> settledOrders,
    String unavailableReason
) {
    public ExchangeExposure {
        currentExposure = currentExposure == null ? BigDecimal.ZERO : currentExposure;
        realizedProfitLoss = realizedProfitLoss == null ? BigDecimal.ZERO : realizedProfitLoss;
        positions = positions == null ? List.of() : List.copyOf(positions);
        settledOrders = settledOrders == null ? List.of() : List.copyOf(settledOrders);
        unavailableReason = unavailableReason == null ? null : unavailableReason.strip();
    }

    public ExchangeExposure(
        boolean available,
        int openPositions,
        BigDecimal currentExposure,
        BigDecimal realizedProfitLoss,
        List<ExchangeExposurePosition> positions,
        Set<String> settledExternalOrderIds,
        String unavailableReason
    ) {
        this(
            available,
            openPositions,
            currentExposure,
            realizedProfitLoss,
            positions,
            settledOrders(settledExternalOrderIds),
            unavailableReason
        );
    }

    public ExchangeExposure(
        boolean available,
        int openPositions,
        BigDecimal currentExposure,
        BigDecimal realizedProfitLoss,
        List<ExchangeExposurePosition> positions,
        String unavailableReason
    ) {
        this(available, openPositions, currentExposure, realizedProfitLoss, positions, List.of(), unavailableReason);
    }

    public static ExchangeExposure unavailable(String reason) {
        return new ExchangeExposure(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), reason);
    }

    public Set<String> settledExternalOrderIds() {
        return settledOrders.stream()
            .map(ExchangeSettledOrder::externalOrderId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<ExchangeSettledOrder> settledOrders(Set<String> settledExternalOrderIds) {
        if (settledExternalOrderIds == null || settledExternalOrderIds.isEmpty()) {
            return List.of();
        }
        return settledExternalOrderIds.stream()
            .map(id -> new ExchangeSettledOrder(id, "", 0L, null, BigDecimal.ZERO, null))
            .toList();
    }
}
