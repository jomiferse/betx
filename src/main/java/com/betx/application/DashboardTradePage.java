package com.betx.application;

import java.util.List;

/** Paginated read-only trade result for the analytics dashboard. */
public record DashboardTradePage(
    List<DashboardTradeView> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public DashboardTradePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
