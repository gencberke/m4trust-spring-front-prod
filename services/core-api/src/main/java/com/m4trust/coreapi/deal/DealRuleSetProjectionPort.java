package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Deal consumes only the authoritative current pointer projection, never history ordering. */
public interface DealRuleSetProjectionPort {
    Optional<CurrentRuleSet> findCurrent(UUID ruleSetVersionId);
    record CurrentRuleSet(UUID id, long version, UUID sourceAnalysisId,
            UUID sourceExtractionResultVersionId, Instant createdAt, UUID createdByUserId,
            UUID previousRuleSetVersionId, int ruleCount) { }
}
