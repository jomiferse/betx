package com.betx.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Aggregated diagnostics for one stake sizing policy/risk/source tuple. */
public record DiagnosticsStakeSizingPolicyResult(
    String policyName,
    String riskProfile,
    String source,
    long decisions,
    long distinctRecommendations,
    long observations,
    BigDecimal avgBaseStake,
    BigDecimal avgCalculatedStake,
    BigDecimal avgFinalStake,
    BigDecimal minCalculatedStake,
    BigDecimal maxCalculatedStake,
    BigDecimal minFinalStake,
    BigDecimal maxFinalStake,
    long wouldBlockCount,
    BigDecimal wouldBlockRate,
    Map<String, Long> decisionReasonBreakdown,
    Map<String, Long> blockReasonBreakdown,
    Map<String, Long> adjustmentBreakdown,
    DiagnosticsStakeSizingMinStakeFloor minStakeFloor,
    DiagnosticsStakeSizingRealJoined realJoined,
    DiagnosticsStakeSizingPaperJoined paperJoined,
    long probabilityAvailableCount,
    long probabilityMissingCount,
    long confidenceAvailableCount,
    long confidenceMissingCount,
    List<DiagnosticsStakeSizingReductionExample> strongestReductions,
    DiagnosticsStakeSizingPolicyStatus status,
    String warning,
    boolean shouldApplyLive
) {
    public DiagnosticsStakeSizingPolicyResult {
        decisionReasonBreakdown = decisionReasonBreakdown == null ? Map.of() : Map.copyOf(decisionReasonBreakdown);
        blockReasonBreakdown = blockReasonBreakdown == null ? Map.of() : Map.copyOf(blockReasonBreakdown);
        adjustmentBreakdown = adjustmentBreakdown == null ? Map.of() : Map.copyOf(adjustmentBreakdown);
        minStakeFloor = minStakeFloor == null ? DiagnosticsStakeSizingMinStakeFloor.empty() : minStakeFloor;
        realJoined = realJoined == null ? DiagnosticsStakeSizingRealJoined.empty() : realJoined;
        paperJoined = paperJoined == null ? DiagnosticsStakeSizingPaperJoined.empty() : paperJoined;
        strongestReductions = strongestReductions == null ? List.of() : List.copyOf(strongestReductions);
        status = status == null ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE : status;
        shouldApplyLive = false;
    }
}
