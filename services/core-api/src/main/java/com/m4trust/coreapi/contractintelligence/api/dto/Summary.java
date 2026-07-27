package com.m4trust.coreapi.contractintelligence.api.dto;

import java.time.Instant;
import java.util.UUID;

public record Summary(
    UUID id,
    long version,
    UUID sourceAnalysisId,
    UUID sourceExtractionResultVersionId,
    Instant createdAt,
    UUID createdByUserId,
    UUID previousRuleSetVersionId,
    int ruleCount) {}
