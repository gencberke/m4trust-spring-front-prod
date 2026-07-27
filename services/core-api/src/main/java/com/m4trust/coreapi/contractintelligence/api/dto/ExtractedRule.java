package com.m4trust.coreapi.contractintelligence.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExtractedRule(
    String ruleReference,
    String category,
    String title,
    String description,
    StructuredValue structuredValue,
    BigDecimal confidence,
    List<Object> sourceReferences,
    LegalBasis legalBasis) {}
