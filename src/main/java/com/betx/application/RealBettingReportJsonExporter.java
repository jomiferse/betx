package com.betx.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exports the real betting report as structured JSON for future web reuse. */
public class RealBettingReportJsonExporter {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public RealBettingReportJsonExporter() {
        this(Clock.systemUTC());
    }

    public RealBettingReportJsonExporter(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public void export(RealBettingReport report, Path exportPath) {
        try {
            Path parent = exportPath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(exportPath.toFile(), payload(report));
        } catch (IOException exc) {
            throw new UncheckedIOException("Could not write JSON report export: " + exportPath, exc);
        }
    }

    private Map<String, Object> payload(RealBettingReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now(clock).toString());
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", instant(report.periodStart()));
        period.put("end", instant(report.periodEnd()));
        period.put("label", report.periodLabel());
        payload.put("period", period);
        payload.put("summary", summary(report));
        payload.put("limitations", report.limitations());
        payload.put("warnings", report.warnings().stream().map(RealBettingReportWarning::message).toList());
        payload.put("rollingWindows", report.rollingWindows().stream().map(this::window).toList());
        payload.put("breakdowns", Map.of(
            "bySelectionSide", segments(report.selectionSideSegments()),
            "byRunner", segments(report.runnerSegments()),
            "byCompetition", segments(report.competitionSegments()),
            "byStrategy", segments(report.strategySegments()),
            "byOdds", segments(report.oddsBandSegments()),
            "byDay", report.dailyPnl().stream().map(this::day).toList()
        ));
        payload.put("rows", report.rows().stream().map(this::row).toList());
        return payload;
    }

    private Map<String, Object> summary(RealBettingReport report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("settledBets", report.settledBets());
        summary.put("openBets", report.openBets());
        summary.put("wins", report.wins());
        summary.put("losses", report.losses());
        summary.put("voidsCancelled", report.voidsCancelled());
        summary.put("turnover", report.totalStaked());
        summary.put("openExposure", report.openExposure());
        summary.put("netRealizedPnl", report.netRealizedPnl());
        summary.put("roi", ratio(report.roiPercent()));
        summary.put("winRate", ratio(report.winRatePercent()));
        summary.put("averageExecutedOdds", report.averageExecutedOdds());
        summary.put("operationalAvailableBalance", report.operationalAvailableBalance());
        summary.put("exchangeAvailableBalance", report.exchangeAvailableBalance());
        summary.put("performanceCurveValue", report.realizedEquity());
        summary.put("peakPerformanceCurveValue", report.peakRealizedEquity());
        summary.put("usesInitialReferenceBalance", report.realizedEquityUsesInitialReference());
        summary.put("maximumDrawdown", report.maximumDrawdown());
        summary.put("currentDrawdown", report.currentDrawdown());
        summary.put("maximumWinningStreak", report.maxWinningStreak());
        summary.put("maximumLosingStreak", report.maxLosingStreak());
        return summary;
    }

    private Map<String, Object> window(RealBettingReportRollingWindow window) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("requestedSize", window.requestedSize());
        value.put("availableSettledBets", window.availableSettledBets());
        value.put("wins", window.wins());
        value.put("losses", window.losses());
        value.put("winRate", ratio(window.winRatePercent()));
        value.put("turnover", window.totalStaked());
        value.put("netRealizedPnl", window.netRealizedPnl());
        value.put("roi", ratio(window.roiPercent()));
        value.put("maximumDrawdown", window.maximumDrawdown());
        value.put("maximumWinningStreak", window.maxWinningStreak());
        value.put("maximumLosingStreak", window.maxLosingStreak());
        return value;
    }

    private List<Map<String, Object>> segments(List<RealBettingReportSegment> segments) {
        return segments.stream().map(segment -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", segment.name());
            value.put("settledBets", segment.settledBets());
            value.put("wins", segment.wins());
            value.put("losses", segment.losses());
            value.put("voids", segment.voids());
            value.put("turnover", segment.totalStaked());
            value.put("netRealizedPnl", segment.netRealizedPnl());
            value.put("roi", ratio(segment.roiPercent()));
            value.put("winRate", ratio(segment.winRatePercent()));
            return value;
        }).toList();
    }

    private Map<String, Object> day(RealBettingReportDailyPnl day) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("day", day.day().toString());
        value.put("settledBets", day.settledBets());
        value.put("pnl", day.pnl());
        return value;
    }

    private Map<String, Object> row(RealBettingReportRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("betIntentId", row.id());
        value.put("eventName", row.eventName());
        value.put("marketName", row.marketName());
        value.put("competitionName", row.competitionName());
        value.put("strategyName", row.strategyName());
        value.put("runnerName", row.runnerName());
        value.put("selectionSide", row.selectionSide().name());
        value.put("stage", row.stage() == null ? null : row.stage().name());
        value.put("settlementResult", row.settlementResult() == null ? null : row.settlementResult().name());
        value.put("selectedStake", row.selectedStake());
        value.put("executedOdds", row.odds());
        value.put("realizedProfitLoss", row.realizedProfitLoss());
        value.put("availableBalance", row.availableBalance());
        value.put("effectiveAvailableBalance", row.effectiveAvailableBalance());
        value.put("createdAt", instant(row.createdAt()));
        value.put("settledAt", instant(row.settledAt()));
        return value;
    }

    private BigDecimal ratio(BigDecimal percent) {
        BigDecimal safe = percent == null ? BigDecimal.ZERO : percent;
        return safe.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
