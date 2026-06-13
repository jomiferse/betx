package com.betx.adapter.backtest;

import com.betx.application.BacktestValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts Football-Data match CSV files into BetX normalized backtest history. */
@Component
public class FootballDataBacktestCsvConverter {
    private static final BigDecimal SPREAD = new BigDecimal("0.04");
    private static final BigDecimal LIQUIDITY = new BigDecimal("1000");
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("d/M/")
        .appendValueReduced(ChronoField.YEAR, 2, 4, 1950)
        .toFormatter(Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);
    private static final String HEADER = "observed_at,exchange,market_id,market_name,event_name,competition_name,"
        + "market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result";

    private final CsvMapper mapper = CsvMapper.builder().findAndAddModules().build();

    public FootballDataConversionResult convert(Path inputPath, Path outputPath) {
        if (inputPath == null || !Files.exists(inputPath)) {
            throw new BacktestValidationException("Football-Data CSV file not found: " + inputPath);
        }
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<String> outputRows = new ArrayList<>();
        outputRows.add(HEADER);
        int matchesRead = 0;
        try (MappingIterator<Map<String, String>> rows = mapper
            .readerFor(new TypeReference<Map<String, String>>() {
            })
            .with(schema)
            .readValues(inputPath.toFile())) {
            while (rows.hasNext()) {
                matchesRead++;
                outputRows.addAll(convertMatch(rows.next()));
            }
            writeOutput(outputPath, outputRows);
            return new FootballDataConversionResult(matchesRead, outputRows.size() - 1);
        } catch (IOException exc) {
            throw new BacktestValidationException("Could not convert Football-Data CSV file: " + inputPath, exc);
        }
    }

    private List<String> convertMatch(Map<String, String> row) {
        String homeTeam = value(row, "HomeTeam");
        String awayTeam = value(row, "AwayTeam");
        String result = value(row, "FTR");
        BigDecimal openingHome = decimal(row, "B365H");
        BigDecimal openingDraw = decimal(row, "B365D");
        BigDecimal openingAway = decimal(row, "B365A");
        BigDecimal closingHome = decimal(row, "B365CH");
        BigDecimal closingDraw = decimal(row, "B365CD");
        BigDecimal closingAway = decimal(row, "B365CA");
        if (homeTeam == null || awayTeam == null || result == null
            || openingHome == null || openingDraw == null || openingAway == null
            || closingHome == null || closingDraw == null || closingAway == null) {
            return List.of();
        }

        String division = value(row, "Div");
        LocalDate date = date(value(row, "Date"));
        LocalTime kickoffTime = time(value(row, "Time"));
        LocalDateTime kickoff = LocalDateTime.of(date, kickoffTime);
        LocalDateTime openingObservedAt = LocalDateTime.of(date, LocalTime.of(8, 0));
        LocalDateTime closingObservedAt = kickoff.minusHours(2);
        String eventName = homeTeam + " v " + awayTeam;
        String marketId = marketId(division, date, homeTeam, awayTeam);
        List<RunnerOdds> runners = List.of(
            new RunnerOdds(1L, homeTeam, openingHome, closingHome, "H".equalsIgnoreCase(result)),
            new RunnerOdds(2L, "Draw", openingDraw, closingDraw, "D".equalsIgnoreCase(result)),
            new RunnerOdds(3L, awayTeam, openingAway, closingAway, "A".equalsIgnoreCase(result))
        );

        List<String> lines = new ArrayList<>();
        for (RunnerOdds runner : runners) {
            lines.add(normalizedRow(openingObservedAt, marketId, eventName, division, kickoff, runner.selectionId(),
                runner.name(), runner.openingOdds(), runner.won()));
            lines.add(normalizedRow(closingObservedAt, marketId, eventName, division, kickoff, runner.selectionId(),
                runner.name(), runner.closingOdds(), runner.won()));
        }
        return lines;
    }

    private String normalizedRow(
        LocalDateTime observedAt,
        String marketId,
        String eventName,
        String division,
        LocalDateTime kickoff,
        long selectionId,
        String runnerName,
        BigDecimal bestBackPrice,
        boolean won
    ) {
        return String.join(",",
            observedAt.toInstant(ZoneOffset.UTC).toString(),
            "football-data",
            marketId,
            "Match Odds",
            eventName,
            division == null ? "unknown" : division,
            kickoff.toInstant(ZoneOffset.UTC).toString(),
            Long.toString(selectionId),
            runnerName,
            decimal(bestBackPrice),
            decimal(layPrice(bestBackPrice)),
            decimal(SPREAD),
            LIQUIDITY.stripTrailingZeros().toPlainString(),
            won ? "WIN" : "LOSE"
        );
    }

    private String value(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private BigDecimal decimal(Map<String, String> row, String key) {
        String value = value(row, key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exc) {
            return null;
        }
    }

    private LocalDate date(String value) {
        if (value == null) {
            throw new BacktestValidationException("Date is required");
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exc) {
            throw new BacktestValidationException("Date must use Football-Data format dd/mm/yy", exc);
        }
    }

    private LocalTime time(String value) {
        if (value == null) {
            return LocalTime.NOON;
        }
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException exc) {
            return LocalTime.NOON;
        }
    }

    private String marketId(String division, LocalDate date, String homeTeam, String awayTeam) {
        return (division == null ? "football-data" : division)
            + "-"
            + date
            + "-"
            + slug(homeTeam)
            + "-"
            + slug(awayTeam);
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private BigDecimal layPrice(BigDecimal backPrice) {
        return backPrice.multiply(BigDecimal.ONE.add(SPREAD)).setScale(2, RoundingMode.HALF_UP);
    }

    private String decimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void writeOutput(Path outputPath, List<String> rows) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, String.join(System.lineSeparator(), rows) + System.lineSeparator());
    }

    private record RunnerOdds(
        long selectionId,
        String name,
        BigDecimal openingOdds,
        BigDecimal closingOdds,
        boolean won
    ) {
    }
}
