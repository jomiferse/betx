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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        + "season,odds_source,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result";

    private final CsvMapper mapper = CsvMapper.builder().findAndAddModules().build();

    public FootballDataConversionResult convert(Path inputPath, Path outputPath) {
        return convert(inputPath, outputPath, null, FootballDataOddsSource.CLOSING_AVERAGE);
    }

    public FootballDataConversionResult convert(
        Path inputPath,
        Path outputPath,
        String season,
        FootballDataOddsSource oddsSource
    ) {
        return convert(List.of(inputPath), outputPath, season, oddsSource);
    }

    public FootballDataConversionResult convert(
        List<Path> inputPaths,
        Path outputPath,
        String season,
        FootballDataOddsSource oddsSource
    ) {
        FootballDataOddsSource selectedOddsSource = oddsSource == null ? FootballDataOddsSource.CLOSING_AVERAGE : oddsSource;
        return convert(inputPaths, outputPath, season, List.of(selectedOddsSource));
    }

    public FootballDataConversionResult convert(
        List<Path> inputPaths,
        Path outputPath,
        String season,
        String oddsSource
    ) {
        String selectedOddsSource = oddsSource == null || oddsSource.isBlank() ? FootballDataOddsSource.CLOSING_AVERAGE.id() : oddsSource.strip();
        if ("opening-closing".equalsIgnoreCase(selectedOddsSource)) {
            return convert(
                inputPaths,
                outputPath,
                season,
                List.of(FootballDataOddsSource.OPENING_BOOKMAKER, FootballDataOddsSource.CLOSING_AVERAGE)
            );
        }
        return convert(inputPaths, outputPath, season, FootballDataOddsSource.fromId(selectedOddsSource));
    }

    private FootballDataConversionResult convert(
        List<Path> inputPaths,
        Path outputPath,
        String season,
        List<FootballDataOddsSource> oddsSources
    ) {
        List<Path> safeInputPaths = inputPaths == null ? List.of() : List.copyOf(inputPaths);
        if (safeInputPaths.isEmpty()) {
            throw new BacktestValidationException("At least one Football-Data CSV file is required.");
        }
        List<FootballDataOddsSource> selectedOddsSources = oddsSources == null || oddsSources.isEmpty()
            ? List.of(FootballDataOddsSource.CLOSING_AVERAGE)
            : List.copyOf(oddsSources);
        List<String> outputRows = new ArrayList<>();
        outputRows.add(HEADER);
        Set<String> matchKeys = new HashSet<>();
        int matchesRead = 0;
        int duplicatesSkipped = 0;
        for (Path inputPath : safeInputPaths) {
            ConversionChunk chunk = convertInput(inputPath, season, selectedOddsSources, matchKeys);
            matchesRead += chunk.matchesRead();
            duplicatesSkipped += chunk.duplicatesSkipped();
            outputRows.addAll(chunk.rows());
        }
        try {
            writeOutput(outputPath, outputRows);
        } catch (IOException exc) {
            throw new BacktestValidationException("Could not write Football-Data conversion output: " + outputPath, exc);
        }
        return new FootballDataConversionResult(matchesRead, outputRows.size() - 1, duplicatesSkipped);
    }

    private ConversionChunk convertInput(
        Path inputPath,
        String season,
        List<FootballDataOddsSource> oddsSources,
        Set<String> matchKeys
    ) {
        if (inputPath == null || !Files.exists(inputPath)) {
            throw new BacktestValidationException("Football-Data CSV file not found: " + inputPath);
        }
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<String> rowsWritten = new ArrayList<>();
        int matchesRead = 0;
        int duplicatesSkipped = 0;
        try (MappingIterator<Map<String, String>> rows = mapper
            .readerFor(new TypeReference<Map<String, String>>() {
            })
            .with(schema)
            .readValues(inputPath.toFile())) {
            while (rows.hasNext()) {
                Map<String, String> row = rows.next();
                matchesRead++;
                String matchKey = matchKey(row, season);
                if (matchKey != null && !matchKeys.add(matchKey)) {
                    duplicatesSkipped++;
                    continue;
                }
                for (FootballDataOddsSource oddsSource : oddsSources) {
                    rowsWritten.addAll(convertMatch(row, season, oddsSource));
                }
            }
            return new ConversionChunk(matchesRead, duplicatesSkipped, rowsWritten);
        } catch (IOException exc) {
            throw new BacktestValidationException("Could not convert Football-Data CSV file: " + inputPath, exc);
        }
    }

    private List<String> convertMatch(Map<String, String> row, String season, FootballDataOddsSource oddsSource) {
        String homeTeam = value(row, "HomeTeam");
        String awayTeam = value(row, "AwayTeam");
        String result = value(row, "FTR");
        BigDecimal homeOdds = decimal(row, oddsSource.homeColumn());
        BigDecimal drawOdds = decimal(row, oddsSource.drawColumn());
        BigDecimal awayOdds = decimal(row, oddsSource.awayColumn());
        if (homeTeam == null || awayTeam == null || result == null
            || homeOdds == null || drawOdds == null || awayOdds == null) {
            return List.of();
        }

        String division = value(row, "Div");
        LocalDate date = date(value(row, "Date"));
        LocalTime kickoffTime = time(value(row, "Time"));
        LocalDateTime kickoff = LocalDateTime.of(date, kickoffTime);
        LocalDateTime observedAt = kickoff.minusHours(oddsSource.observedHoursBeforeKickoff());
        String eventName = homeTeam + " v " + awayTeam;
        String seasonLabel = season == null || season.isBlank() ? seasonLabel(date) : season.strip();
        String marketId = marketId(division, seasonLabel, date, homeTeam, awayTeam);
        List<RunnerOdds> runners = List.of(
            new RunnerOdds(1L, homeTeam, homeOdds, "H".equalsIgnoreCase(result)),
            new RunnerOdds(2L, "Draw", drawOdds, "D".equalsIgnoreCase(result)),
            new RunnerOdds(3L, awayTeam, awayOdds, "A".equalsIgnoreCase(result))
        );

        List<String> lines = new ArrayList<>();
        for (RunnerOdds runner : runners) {
            lines.add(normalizedRow(observedAt, marketId, eventName, division, seasonLabel, oddsSource, kickoff, runner.selectionId(),
                runner.name(), runner.odds(), runner.won()));
        }
        return lines;
    }

    private String normalizedRow(
        LocalDateTime observedAt,
        String marketId,
        String eventName,
        String division,
        String season,
        FootballDataOddsSource oddsSource,
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
            season,
            oddsSource.id(),
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

    private String matchKey(Map<String, String> row, String season) {
        String homeTeam = value(row, "HomeTeam");
        String awayTeam = value(row, "AwayTeam");
        String division = value(row, "Div");
        String rawDate = value(row, "Date");
        if (homeTeam == null || awayTeam == null || rawDate == null) {
            return null;
        }
        LocalDate date = date(rawDate);
        String seasonLabel = season == null || season.isBlank() ? seasonLabel(date) : season.strip();
        return (division == null ? "unknown" : division)
            + "|" + seasonLabel
            + "|" + date
            + "|" + slug(homeTeam)
            + "|" + slug(awayTeam);
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

    private String marketId(String division, String season, LocalDate date, String homeTeam, String awayTeam) {
        return (division == null ? "football-data" : division)
            + "-"
            + slug(season)
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

    private String seasonLabel(LocalDate date) {
        int startYear = date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;
        return startYear + "/" + String.format("%02d", (startYear + 1) % 100);
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
        BigDecimal odds,
        boolean won
    ) {
    }

    private record ConversionChunk(int matchesRead, int duplicatesSkipped, List<String> rows) {
    }
}
