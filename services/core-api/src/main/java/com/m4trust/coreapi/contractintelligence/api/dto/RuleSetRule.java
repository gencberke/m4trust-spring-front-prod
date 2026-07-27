package com.m4trust.coreapi.contractintelligence.api.dto;

public record RuleSetRule(
    String ruleReference,
    String decision,
    String category,
    String title,
    String description,
    StructuredValue structuredValue,
    LegalBasis legalBasis,
    String legalBasisProvenance) {}
