package com.betx.adapter.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FootballDataBacktestCsvConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsFootballDataRowsToNormalizedBacktestHistoryWithSeasonAndClosingOddsSource() throws Exception {
        Path input = tempDir.resolve("SP1.csv");
        Path output = tempDir.resolve("history.csv");
        Files.writeString(input, """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,2.20,3.50,3.30
            """);

        FootballDataBacktestCsvConverter converter = new FootballDataBacktestCsvConverter();

        FootballDataConversionResult result = converter.convert(input, output, "2025/26", FootballDataOddsSource.CLOSING_AVERAGE);

        assertThat(result.matchesRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isEqualTo(3);
        assertThat(result.duplicatesSkipped()).isZero();
        assertThat(Files.readString(output))
            .contains("observed_at,exchange,market_id,market_name,event_name,competition_name,season,odds_source,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result")
            .contains("2025-08-15T18:30:00Z,football-data,SP1-2025-26-2025-08-15-girona-rayo-vallecano,Match Odds,Girona v Rayo Vallecano,SP1,2025/26,closing-average,2025-08-15T20:30:00Z,")
            .contains(",Rayo Vallecano,3.30,3.43,0.04,1000,WIN")
            .contains(",Draw,3.50,3.64,0.04,1000,LOSE");
    }

    @Test
    void convertsOpeningOddsSeparatelyFromClosingAverageOdds() throws Exception {
        Path input = tempDir.resolve("SP1.csv");
        Path output = tempDir.resolve("opening.csv");
        Files.writeString(input, """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,2.20,3.50,3.30
            """);

        FootballDataConversionResult result = new FootballDataBacktestCsvConverter()
            .convert(input, output, "2025/26", FootballDataOddsSource.OPENING_BOOKMAKER);

        assertThat(result.rowsWritten()).isEqualTo(3);
        assertThat(Files.readString(output))
            .contains(",SP1,2025/26,opening-bookmaker,2025-08-15T20:30:00Z,1,Girona,2.10,2.18,0.04,1000,LOSE")
            .doesNotContain("3.50,3.64");
    }

    @Test
    void convertsOpeningAndClosingOddsAsPairedChronologicalObservations() throws Exception {
        Path input = tempDir.resolve("SP1.csv");
        Path output = tempDir.resolve("paired.csv");
        Files.writeString(input, """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,2.20,3.50,3.30
            """);

        FootballDataConversionResult result = new FootballDataBacktestCsvConverter()
            .convert(List.of(input), output, "2025/26", "opening-closing");

        assertThat(result.rowsWritten()).isEqualTo(6);
        List<String> lines = Files.readAllLines(output);
        assertThat(lines.get(1)).contains("2025-08-15T08:30:00Z").contains(",opening-bookmaker,");
        assertThat(lines.get(4)).contains("2025-08-15T18:30:00Z").contains(",closing-average,");
    }

    @Test
    void suppressesDuplicateMatchesWhenCombiningDatasets() throws Exception {
        Path first = tempDir.resolve("SP1-a.csv");
        Path second = tempDir.resolve("SP1-b.csv");
        Path output = tempDir.resolve("combined.csv");
        String csv = """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,2.20,3.50,3.30
            """;
        Files.writeString(first, csv);
        Files.writeString(second, csv);

        FootballDataConversionResult result = new FootballDataBacktestCsvConverter()
            .convert(List.of(first, second), output, "2025/26", FootballDataOddsSource.CLOSING_AVERAGE);

        assertThat(result.matchesRead()).isEqualTo(2);
        assertThat(result.duplicatesSkipped()).isEqualTo(1);
        assertThat(result.rowsWritten()).isEqualTo(3);
        assertThat(Files.readString(output).lines()).hasSize(4);
    }

    @Test
    void skipsRowsWithoutCompleteOpeningAndClosingOdds() throws Exception {
        Path input = tempDir.resolve("SP1.csv");
        Path output = tempDir.resolve("history.csv");
        Files.writeString(input, """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,,3.50,3.30
            """);

        FootballDataBacktestCsvConverter converter = new FootballDataBacktestCsvConverter();

        FootballDataConversionResult result = converter.convert(input, output, "2025/26", FootballDataOddsSource.CLOSING_AVERAGE);

        assertThat(result.matchesRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isZero();
        assertThat(Files.readString(output).lines()).hasSize(1);
    }
}
