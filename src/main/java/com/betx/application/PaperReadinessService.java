package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.PaperTradeRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.PaperReadinessGateConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaperReadinessService implements EvaluatePaperReadinessUseCase {
    public static final String STRATEGY = "value-football-draw-only";

    private final BetxConfigRepository configRepository;
    private final PaperTradeRepository paperTradeRepository;

    public PaperReadinessService(BetxConfigRepository configRepository, PaperTradeRepository paperTradeRepository) {
        this.configRepository = configRepository;
        this.paperTradeRepository = paperTradeRepository;
    }

    @Override
    public PaperReadinessResult evaluate(ConfigPath configPath, String strategy) {
        BetxConfig config = configRepository.load(configPath);
        PaperReadinessGateConfig gate = config.paper().readinessGate();
        String effectiveStrategy = strategy == null || strategy.isBlank() ? STRATEGY : strategy;
        if (!gate.enabled()) {
            return result(
                PaperReadinessStatus.DISABLED,
                effectiveStrategy,
                0,
                gate,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                "DISABLED",
                List.of("Paper readiness gate is disabled.")
            );
        }

        List<PaperTrade> allTrades = paperTradeRepository.listAll(config.storage().path());
        List<BacktestPaperTrade> paperTrades = allTrades.stream()
            .map(PaperTrade::toBacktestPaperTrade)
            .toList();
        List<BacktestPaperTrade> settledTrades = paperTrades.stream()
            .filter(trade -> trade.result() != null)
            .toList();
        BacktestClvSummary clvSummary = BacktestClvSummary.from(paperTrades.stream()
            .filter(trade -> trade.decimalClvRatio() != null)
            .toList());
        BacktestPaperValidationReport validation = new BacktestComparisonReport(
            42L,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            "exchange",
            "live",
            BacktestDatasetCapability.EXCHANGE_SNAPSHOTS,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new BacktestLeakageDiagnostics(0, 0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            paperTrades,
            clvSummary,
            List.of(),
            List.of()
        ).paperValidation(BigDecimal.valueOf(gate.minimumSettledTrades()));
        BigDecimal rollingRoi = rollingRoi(settledTrades, gate.rollingWindowSize());

        List<String> reasons = new ArrayList<>();
        boolean hasPersistedExecutionFailure = allTrades.stream()
            .anyMatch(trade -> trade.status() == PaperTradeStatus.EXECUTION_FAILED);
        if (gate.blockOnExecutionFailure()
            && (validation.status() == BacktestPaperValidationStatus.EXECUTION_FAILURE || hasPersistedExecutionFailure)) {
            if (validation.status() == BacktestPaperValidationStatus.EXECUTION_FAILURE) {
                reasons.add("Execution evidence reports an execution failure.");
            }
            if (hasPersistedExecutionFailure) {
                reasons.add("At least one persisted paper trade has execution failed.");
            }
            return result(
                PaperReadinessStatus.BLOCKED,
                effectiveStrategy,
                validation.settledTrades(),
                gate,
                validation.executableRoiPercent(),
                validation.medianClv(),
                rollingRoi,
                validation.status().name(),
                reasons
            );
        }

        if (validation.settledTrades() < gate.minimumSettledTrades()) {
            reasons.add("Minimum settled trades not reached.");
            return result(
                PaperReadinessStatus.INSUFFICIENT_DATA,
                effectiveStrategy,
                validation.settledTrades(),
                gate,
                validation.executableRoiPercent(),
                validation.medianClv(),
                rollingRoi,
                validation.status().name(),
                reasons
            );
        }

        BigDecimal requiredExecutableRoiPercent = percent(gate.minimumExecutableRoi());
        BigDecimal requiredRollingRoiPercent = percent(gate.minimumRollingRoi());
        if (!gate.requiredEvidenceStatus().equals(validation.status().name())) {
            reasons.add("Evidence status is below the configured requirement.");
        }
        if (validation.executableRoiPercent().compareTo(requiredExecutableRoiPercent) < 0) {
            reasons.add("Executable ROI is below the configured threshold.");
        }
        BigDecimal medianClv = validation.medianClv() == null ? BigDecimal.ZERO : validation.medianClv();
        if (medianClv.compareTo(gate.minimumMedianClv()) < 0) {
            reasons.add("Median CLV is below the configured threshold.");
        }
        if (rollingRoi.compareTo(requiredRollingRoiPercent) < 0) {
            reasons.add("Rolling ROI is below the configured threshold.");
        }
        if (!reasons.isEmpty()) {
            return result(
                PaperReadinessStatus.NOT_READY,
                effectiveStrategy,
                validation.settledTrades(),
                gate,
                validation.executableRoiPercent(),
                validation.medianClv(),
                rollingRoi,
                validation.status().name(),
                reasons
            );
        }

        return result(
            PaperReadinessStatus.READY,
            effectiveStrategy,
            validation.settledTrades(),
            gate,
            validation.executableRoiPercent(),
            validation.medianClv(),
            rollingRoi,
            validation.status().name(),
            List.of("Paper readiness gate passed.")
        );
    }

    private PaperReadinessResult result(
        PaperReadinessStatus status,
        String strategy,
        int settledTrades,
        PaperReadinessGateConfig gate,
        BigDecimal executableRoi,
        BigDecimal medianClv,
        BigDecimal rollingRoi,
        String evidenceStatus,
        List<String> reasons
    ) {
        return new PaperReadinessResult(
            status,
            strategy,
            settledTrades,
            gate.minimumSettledTrades(),
            executableRoi,
            percent(gate.minimumExecutableRoi()),
            medianClv,
            gate.minimumMedianClv(),
            rollingRoi,
            percent(gate.minimumRollingRoi()),
            evidenceStatus,
            reasons
        );
    }

    private BigDecimal rollingRoi(List<BacktestPaperTrade> settledTrades, int windowSize) {
        List<BacktestPaperTrade> window = settledTrades.stream()
            .sorted(Comparator.comparing(BacktestPaperTrade::recommendationTimestamp))
            .skip(Math.max(0, settledTrades.size() - windowSize))
            .toList();
        if (window.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pnl = window.stream()
            .map(BacktestPaperTrade::netPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return pnl.divide(BigDecimal.valueOf(window.size()).multiply(BigDecimal.valueOf(5)), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal ratio) {
        return (ratio == null ? BigDecimal.ZERO : ratio)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
