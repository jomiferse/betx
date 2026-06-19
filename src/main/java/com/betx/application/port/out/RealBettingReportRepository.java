package com.betx.application.port.out;

import com.betx.application.RealBettingReportRow;
import java.util.List;

/** Reads persisted real betting rows for reporting without modifying state. */
public interface RealBettingReportRepository {
    List<RealBettingReportRow> listReportRows(String databasePath);
}
