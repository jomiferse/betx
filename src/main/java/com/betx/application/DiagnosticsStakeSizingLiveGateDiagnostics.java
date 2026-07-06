package com.betx.application;

import java.math.BigDecimal;
import java.util.List;

/** Read-only diagnostics projection of the stake sizing live gate evaluator. */
public record DiagnosticsStakeSizingLiveGateDiagnostics(
    boolean enabled,
    boolean liveEnabled,
    boolean stakingEnabled,
    boolean shadowEnabled,
    String candidatePolicy,
    String candidateRiskProfile,
    String gateStatus,
    boolean gatePassed,
    boolean conceptuallyEligibleForLive,
    boolean shouldApplyLive,
    boolean officiallyApplied,
    String selectedStakeMode,
    boolean fallbackApplied,
    BigDecimal fallbackStake,
    BigDecimal representativeFinalStakeUsed,
    String representativeStakeSource,
    DiagnosticsStakeSizingLiveGateSample sample,
    DiagnosticsStakeSizingLiveGateHealth health,
    DiagnosticsStakeSizingLiveGateRisk risk,
    DiagnosticsStakeSizingLiveGateBudget budget,
    DiagnosticsStakeSizingLiveGateExposure exposure,
    DiagnosticsStakeSizingLiveGateKillSwitch killSwitch,
    boolean stakeMismatchActive,
    List<String> reasons,
    List<String> warnings,
    DiagnosticsStakeSizingLiveGateDryRun dryRun
) {
    public DiagnosticsStakeSizingLiveGateDiagnostics {
        shouldApplyLive = false;
        officiallyApplied = false;
        sample = sample == null ? new DiagnosticsStakeSizingLiveGateSample(0, 100) : sample;
        health = health == null ? new DiagnosticsStakeSizingLiveGateHealth(0, 0, 0, false) : health;
        risk = risk == null ? new DiagnosticsStakeSizingLiveGateRisk(BigDecimal.ZERO, BigDecimal.ZERO) : risk;
        budget = budget == null
            ? new DiagnosticsStakeSizingLiveGateBudget(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false)
            : budget;
        exposure = exposure == null ? new DiagnosticsStakeSizingLiveGateExposure(0, false) : exposure;
        killSwitch = killSwitch == null ? new DiagnosticsStakeSizingLiveGateKillSwitch(false, List.of()) : killSwitch;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        dryRun = dryRun == null ? DiagnosticsStakeSizingLiveGateDryRun.empty() : dryRun;
    }

    public static DiagnosticsStakeSizingLiveGateDiagnostics empty() {
        return new DiagnosticsStakeSizingLiveGateDiagnostics(
            false,
            false,
            false,
            true,
            "RISK_ADJUSTED",
            "CONSERVATIVE",
            "FAIL",
            false,
            false,
            false,
            false,
            "FIXED_FALLBACK",
            true,
            new BigDecimal("1.00"),
            new BigDecimal("1.00"),
            "SCENARIO_BASE_5_MIN_1/RISK_ADJUSTED/CONSERVATIVE",
            new DiagnosticsStakeSizingLiveGateSample(0, 100),
            new DiagnosticsStakeSizingLiveGateHealth(0, 0, 0, false),
            new DiagnosticsStakeSizingLiveGateRisk(BigDecimal.ZERO, new BigDecimal("25.00")),
            new DiagnosticsStakeSizingLiveGateBudget(new BigDecimal("25.00"), new BigDecimal("50.00"), new BigDecimal("5.00"), true),
            new DiagnosticsStakeSizingLiveGateExposure(10, true),
            new DiagnosticsStakeSizingLiveGateKillSwitch(false, List.of()),
            false,
            List.of(),
            List.of(),
            DiagnosticsStakeSizingLiveGateDryRun.empty()
        );
    }
}
