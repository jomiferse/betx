package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.adapter.backtest.FootballDataBacktestCsvConverter;
import com.betx.adapter.backtest.FootballDataConversionResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FootballDataBacktestConvertCommandTest {
    @Test
    void printsConversionSummary() {
        RecordingConverter converter = new RecordingConverter(new FootballDataConversionResult(12, 60));
        FootballDataBacktestConvertCommand command = new FootballDataBacktestConvertCommand(converter);
        command.inputPath = Path.of("SP1.csv");
        command.outputPath = Path.of("history.csv");

        String output = captureOutput(command::run);

        assertThat(converter.inputPaths()).containsExactly(Path.of("SP1.csv"));
        assertThat(converter.outputPaths()).containsExactly(Path.of("history.csv"));
        assertThat(output).contains("Football-Data conversion complete | matches=12 | rows=60 | output=history.csv");
    }

    private static String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }

    private static final class RecordingConverter extends FootballDataBacktestCsvConverter {
        private final FootballDataConversionResult result;
        private final List<Path> inputPaths = new ArrayList<>();
        private final List<Path> outputPaths = new ArrayList<>();

        private RecordingConverter(FootballDataConversionResult result) {
            this.result = result;
        }

        @Override
        public FootballDataConversionResult convert(Path inputPath, Path outputPath) {
            inputPaths.add(inputPath);
            outputPaths.add(outputPath);
            return result;
        }

        private List<Path> inputPaths() {
            return inputPaths;
        }

        private List<Path> outputPaths() {
            return outputPaths;
        }
    }

}
