package com.m4trust.coreapi.contractintelligence.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Version(
    UUID id,
    long version,
    UUID sourceAnalysisId,
    UUID sourceExtractionResultVersionId,
    Instant createdAt,
    UUID createdByUserId,
    UUID previousRuleSetVersionId,
    int ruleCount,
    List<RuleSetRule> rules,
    List<String> excludedRuleReferences) {}
