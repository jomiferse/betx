package com.betx.adapter.web;

import com.betx.application.DashboardAnalyticsService;
import com.betx.application.DashboardSummaryView;
import com.betx.application.DashboardTradePage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {
    @Test
    void exposesReadOnlyDashboardRoutes() throws Exception {
        DashboardAnalyticsService service = Mockito.mock(DashboardAnalyticsService.class);
        Mockito.when(service.summary("30D")).thenReturn(new DashboardSummaryView(
            new BigDecimal("9.95"),
            new BigDecimal("12.28"),
            97,
            38,
            43,
            new BigDecimal("46.91"),
            new BigDecimal("81.00"),
            new BigDecimal("5.00"),
            new BigDecimal("15.00"),
            Instant.parse("2026-06-25T10:56:38Z")
        ));
        Mockito.when(service.equity("30D")).thenReturn(List.of());
        Mockito.when(service.dailyPnl("30D")).thenReturn(List.of());
        Mockito.when(service.strategyBreakdown("30D")).thenReturn(List.of());
        Mockito.when(service.trades("30D", 1, 50, "SETTLED", "WIN", "value-football", "real", "pnl", "desc"))
            .thenReturn(new DashboardTradePage(List.of(), 1, 50, 0, 0));
        var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard/summary?range=30D").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPnl").value(9.95))
            .andExpect(jsonPath("$.totalTrades").value(97));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard/equity?range=30D").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard/daily-pnl?range=30D").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard/breakdown/strategy?range=30D").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard/trades")
                .queryParam("range", "30D")
                .queryParam("page", "1")
                .queryParam("size", "50")
                .queryParam("status", "SETTLED")
                .queryParam("result", "WIN")
                .queryParam("strategy", "value-football")
                .queryParam("search", "real")
                .queryParam("sort", "pnl")
                .queryParam("order", "desc")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(50));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/dashboard/summary").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed());

        Mockito.verify(service).summary("30D");
        Mockito.verify(service).equity("30D");
        Mockito.verify(service).dailyPnl("30D");
        Mockito.verify(service).strategyBreakdown("30D");
        Mockito.verify(service).trades("30D", 1, 50, "SETTLED", "WIN", "value-football", "real", "pnl", "desc");
    }
}
