package com.betx.cli;

import com.betx.application.EvaluatePaperReadinessUseCase;
import com.betx.application.PaperReadinessResult;
import com.betx.application.PaperReadinessService;
import com.betx.application.PaperReadinessStatus;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "paper-readiness", description = "Inspect paper evidence for internal strategy validation.")
public class PaperReadinessCommand implements Runnable {
    private final EvaluatePaperReadinessUseCase readinessUseCase;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Autowired
    public PaperReadinessCommand(EvaluatePaperReadinessUseCase readinessUseCase) {
        this.readinessUseCase = readinessUseCase;
    }

    @Override
    public void run() {
        PaperReadinessResult result = readinessUseCase.evaluate(new ConfigPath(configPath), PaperReadinessService.STRATEGY);
        System.out.println("Paper readiness");
        System.out.println("Strategy: " + result.strategy());
        System.out.println("Status: " + result.status());
        System.out.println("Settled trades: " + result.settledTrades() + " / " + result.requiredSettledTrades());
        System.out.println("Executable ROI: " + signedPercent(result.executableRoi()) + " / " + signedPercent(result.requiredExecutableRoi()));
        System.out.println("Median CLV: " + signedClv(result.medianClv()) + " / >= " + clvThreshold(result.requiredMedianClv()));
        System.out.println("Rolling ROI: " + signedPercent(result.rollingRoi()) + " / >= " + percent(result.requiredRollingRoi()));
        System.out.println("Evidence status: " + result.evidenceStatus());
        System.out.println("Auto-betting: " + autoBettingOutcome(result.status()));
        System.out.println();
        System.out.println("Reasons:");
        result.reasons().forEach(reason -> System.out.println("- " + reason));
    }

    private String autoBettingOutcome(PaperReadinessStatus status) {
        return switch (status) {
            case READY -> "ALLOWED";
            case DISABLED -> "NOT_APPLICABLE";
            case NOT_READY, INSUFFICIENT_DATA, BLOCKED -> "BLOCKED";
        };
    }

    private String signedPercent(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return (safe.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + percent(safe);
    }

    private String percent(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String signedClv(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return (safe.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + clv(safe);
    }

    private String clv(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }

    private String clvThreshold(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
