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
    Set<String> settledExternalOrderIds,
    String unavailableReason
) {
    public ExchangeExposure {
        currentExposure = currentExposure == null ? BigDecimal.ZERO : currentExposure;
        realizedProfitLoss = realizedProfitLoss == null ? BigDecimal.ZERO : realizedProfitLoss;
        positions = positions == null ? List.of() : List.copyOf(positions);
        settledExternalOrderIds = settledExternalOrderIds == null ? Set.of() : Set.copyOf(settledExternalOrderIds);
        unavailableReason = unavailableReason == null ? null : unavailableReason.strip();
    }

    public ExchangeExposure(
        boolean available,
        int openPositions,
        BigDecimal currentExposure,
        BigDecimal realizedProfitLoss,
        List<ExchangeExposurePosition> positions,
        String unavailableReason
    ) {
        this(available, openPositions, currentExposure, realizedProfitLoss, positions, Set.of(), unavailableReason);
    }

    public static ExchangeExposure unavailable(String reason) {
        return new ExchangeExposure(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), Set.of(), reason);
    }
}
