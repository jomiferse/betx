package com.betx.application.port.out;

import com.betx.application.StakeSizingShadowDecision;
import com.betx.application.StakeSizingShadowDecisionUpsertResult;
import java.time.Instant;
import java.util.List;

public interface StakeSizingShadowDecisionRepository {
    StakeSizingShadowDecisionUpsertResult upsert(String databasePath, StakeSizingShadowDecision decision);

    List<StakeSizingShadowDecision> list(String databasePath, Instant from, Instant to);

    long countDuplicateLogicalKeys(String databasePath);
}
