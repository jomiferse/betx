package com.betx.adapter.web;

import com.betx.application.DashboardAnalyticsService;
import com.betx.application.DashboardBreakdownItem;
import com.betx.application.DashboardDailyPnlPoint;
import com.betx.application.DashboardEquityPoint;
import com.betx.application.DashboardSummaryView;
import com.betx.application.DashboardTradePage;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class DashboardController {
    private final DashboardAnalyticsService analyticsService;

    public DashboardController(DashboardAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public DashboardSummaryView summary(@RequestParam(defaultValue = "30D") String range) {
        return analyticsService.summary(range);
    }

    @GetMapping("/equity")
    public List<DashboardEquityPoint> equity(@RequestParam(defaultValue = "30D") String range) {
        return analyticsService.equity(range);
    }

    @GetMapping("/daily-pnl")
    public List<DashboardDailyPnlPoint> dailyPnl(@RequestParam(defaultValue = "30D") String range) {
        return analyticsService.dailyPnl(range);
    }

    @GetMapping("/breakdown/strategy")
    public List<DashboardBreakdownItem> strategyBreakdown(@RequestParam(defaultValue = "30D") String range) {
        return analyticsService.strategyBreakdown(range);
    }

    @GetMapping("/trades")
    public DashboardTradePage trades(
        @RequestParam(defaultValue = "30D") String range,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String strategy,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "timestamp") String sort,
        @RequestParam(defaultValue = "desc") String order
    ) {
        return analyticsService.trades(range, page, size, status, result, strategy, search, sort, order);
    }
}
