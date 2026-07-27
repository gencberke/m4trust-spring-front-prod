package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;

public record AcceptEvidenceRequest(
    @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion,
    @Min(value = 0, message = "expectedEvidenceVersion must be non-negative")
        long expectedEvidenceVersion) {}
