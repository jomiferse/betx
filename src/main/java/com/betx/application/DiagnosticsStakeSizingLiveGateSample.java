package com.betx.application;

/** Joined real-bet sample used by stake sizing live gate diagnostics. */
public record DiagnosticsStakeSizingLiveGateSample(
    long realSettledJoined,
    int minSettledJoinedRequired
) {
}
