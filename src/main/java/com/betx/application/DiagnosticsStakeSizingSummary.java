package com.betx.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Top-level read-only summary for stake sizing shadow diagnostics. */
public record DiagnosticsStakeSizingSummary(
    long decisions,
    long distinctRecommendations,
    Set<String> policies,
    Set<String> riskProfiles,
    Set<String> sources,
    long totalObservedCount,
    Instant firstCreatedAt,
    Instant lastEvaluatedAt,
    Duration freshness,
    long duplicateLogicalKeys,
    long shadowFailures,
    long forbiddenLiveStakeEvents
) {
    public DiagnosticsStakeSizingSummary {
        policies = policies == null ? Set.of() : Set.copyOf(policies);
        riskProfiles = riskProfiles == null ? Set.of() : Set.copyOf(riskProfiles);
        sources = sources == null ? Set.of() : Set.copyOf(sources);
    }

    public static DiagnosticsStakeSizingSummary empty() {
        return new DiagnosticsStakeSizingSummary(0, 0, Set.of(), Set.of(), Set.of(), 0, null, null, null, 0, 0, 0);
    }
}
