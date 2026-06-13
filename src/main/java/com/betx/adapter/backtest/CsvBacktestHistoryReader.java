package com.betx.adapter.backtest;

import com.betx.application.BacktestInputRow;
import com.betx.application.BacktestOutcome;
import com.betx.application.BacktestValidationException;
import com.betx.application.port.out.BacktestHistoryReader;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Reads BetX's normalized historical backtest CSV format. */
@Component
public class CsvBacktestHistoryReader implements BacktestHistoryReader {
    private final CsvMapper mapper = CsvMapper.builder().findAndAddModules().build();

    @Override
    public List<BacktestInputRow> read(Path inputPath) {
        if (inputPath == null || !Files.exists(inputPath)) {
            throw new BacktestValidationException("Backtest CSV file not found: " + inputPath);
        }
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<CsvRow> rows = mapper.readerFor(CsvRow.class).with(schema).readValues(inputPath.toFile())) {
            List<BacktestInputRow> parsedRows = new ArrayList<>();
            int rowNumber = 2;
            while (rows.hasNext()) {
                CsvRow row = rows.next();
                parsedRows.add(toInputRow(row, rowNumber));
                rowNumber++;
            }
            return parsedRows;
        } catch (BacktestValidationException exc) {
            throw exc;
        } catch (IOException | RuntimeException exc) {
            throw new BacktestValidationException("Could not read backtest CSV file: " + inputPath, exc);
        }
    }

    private BacktestInputRow toInputRow(CsvRow row, int rowNumber) {
        try {
            return new BacktestInputRow(
                instant(row.observedAt(), "observed_at"),
                required(row.exchange(), "exchange"),
                required(row.marketId(), "market_id"),
                blankToNull(row.marketName()),
                blankToNull(row.eventName()),
                blankToNull(row.competitionName()),
                optionalInstant(row.marketStartTime(), "market_start_time"),
                positiveLong(row.selectionId(), "selection_id"),
                blankToNull(row.runnerName()),
                decimal(row.bestBackPrice(), "best_back_price"),
                decimal(row.bestLayPrice(), "best_lay_price"),
                decimal(row.spread(), "spread"),
                decimal(row.liquidity(), "liquidity"),
                outcome(row.result())
            );
        } catch (BacktestValidationException exc) {
            throw new BacktestValidationException("Backtest CSV row " + rowNumber + " is invalid: " + exc.getMessage(), exc);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BacktestValidationException(field + " is required");
        }
        return value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Instant instant(String value, String field) {
        try {
            return Instant.parse(required(value, field));
        } catch (java.time.format.DateTimeParseException exc) {
            throw new BacktestValidationException(field + " must be an ISO-8601 instant", exc);
        }
    }

    private Instant optionalInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (java.time.format.DateTimeParseException exc) {
            throw new BacktestValidationException(field + " must be an ISO-8601 instant", exc);
        }
    }

    private long positiveLong(String value, String field) {
        try {
            long parsed = Long.parseLong(required(value, field));
            if (parsed <= 0) {
                throw new BacktestValidationException(field + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException exc) {
            throw new BacktestValidationException(field + " must be a whole number", exc);
        }
    }

    private BigDecimal decimal(String value, String field) {
        try {
            return new BigDecimal(required(value, field));
        } catch (NumberFormatException exc) {
            throw new BacktestValidationException(field + " must be a decimal number", exc);
        }
    }

    private BacktestOutcome outcome(String value) {
        String normalized = required(value, "result").toUpperCase(Locale.ROOT);
        try {
            return BacktestOutcome.valueOf(normalized);
        } catch (IllegalArgumentException exc) {
            throw new BacktestValidationException("result must be WIN or LOSE", exc);
        }
    }

    private record CsvRow(
        @JsonProperty("observed_at") String observedAt,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("market_id") String marketId,
        @JsonProperty("market_name") String marketName,
        @JsonProperty("event_name") String eventName,
        @JsonProperty("competition_name") String competitionName,
        @JsonProperty("market_start_time") String marketStartTime,
        @JsonProperty("selection_id") String selectionId,
        @JsonProperty("runner_name") String runnerName,
        @JsonProperty("best_back_price") String bestBackPrice,
        @JsonProperty("best_lay_price") String bestLayPrice,
        @JsonProperty("spread") String spread,
        @JsonProperty("liquidity") String liquidity,
        @JsonProperty("result") String result
    ) {
    }
}
