package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;

public record RequestVideoAnalysisRequest(
    @Min(value = 0, message = "expectedEvidenceVersion must be non-negative")
        long expectedEvidenceVersion) {}
