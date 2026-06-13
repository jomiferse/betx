package com.betx.application.port.out;

import com.betx.application.BacktestInputRow;
import java.nio.file.Path;
import java.util.List;

/** Reads normalized historical rows for backtesting. */
@FunctionalInterface
public interface BacktestHistoryReader {
    List<BacktestInputRow> read(Path inputPath);
}
