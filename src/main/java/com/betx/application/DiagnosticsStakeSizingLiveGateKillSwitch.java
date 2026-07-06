package com.betx.application;

import java.util.List;

/** Emergency-stop state shown in stake sizing live gate diagnostics. */
public record DiagnosticsStakeSizingLiveGateKillSwitch(
    boolean active,
    List<String> reasons
) {
    public DiagnosticsStakeSizingLiveGateKillSwitch {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
