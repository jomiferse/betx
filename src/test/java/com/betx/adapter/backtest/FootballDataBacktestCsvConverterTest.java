package com.betx.adapter.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FootballDataBacktestCsvConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsFootballDataRowsToNormalizedBacktestHistory() throws Exception {
        Path input = tempDir.resolve("SP1.csv");
        Path output = tempDir.resolve("history.csv");
        Files.writeString(input, """
            Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,B365H,B365D,B365A,B365CH,B365CD,B365CA
            SP1,15/08/25,20:30,Girona,Rayo Vallecano,1,3,A,2.10,3.40,3.60,2.20,3.50,3.30
            """);

        FootballDataBacktestCsvConverter converter = new FootballDataBacktestCsvConverter();

        FootballDataConversionResult result = converter.convert(input, output);

        assertThat(result.matchesRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isEqualTo(6);
        assertThat(Files.readString(output))
            .contains("observed_at,exchange,market_id,market_name,event_name,competition_name,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result")
            .contains("2025-08-15T08:00:00Z,football-data,SP1-2025-08-15-girona-rayo-vallecano,Match Odds,Girona v Rayo Vallecano,SP1,2025-08-15T20:30:00Z,")
            .contains(",Girona,2.10,2.18,0.04,1000,LOSE")
            .contains("2025-08-15T18:30:00Z,football-data,SP1-2025-08-15-girona-rayo-vallecano,Match Odds,Girona v Rayo Vallecano,SP1,2025-08-15T20:30:00Z,")
            .contains(",Rayo Vallecano,3.30,3.43,0.04,1000,WIN")
            .contains(",Draw,3.50,3.64,0.04,1000,LOSE");
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

        FootballDataConversionResult result = converter.convert(input, output);

        assertThat(result.matchesRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isZero();
        assertThat(Files.readString(output).lines()).hasSize(1);
    }
}
