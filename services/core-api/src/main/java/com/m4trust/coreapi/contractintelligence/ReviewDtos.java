package com.m4trust.coreapi.contractintelligence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public Slice 9 DTOs; persistence rows and JSON parsing stay internal. */
final class ReviewDtos {
    private ReviewDtos() { }
    record Review(UUID analysisId, UUID documentId, List<Object> rules) { }
    record History(List<Summary> items) { }
    record Summary(UUID id, long version, UUID sourceAnalysisId,
            UUID sourceExtractionResultVersionId, Instant createdAt,
            UUID createdByUserId, UUID previousRuleSetVersionId, int ruleCount) { }
    record Version(UUID id, long version, UUID sourceAnalysisId,
            UUID sourceExtractionResultVersionId, Instant createdAt,
            UUID createdByUserId, UUID previousRuleSetVersionId, int ruleCount,
            List<Object> rules, List<String> excludedRuleReferences) { }
}
