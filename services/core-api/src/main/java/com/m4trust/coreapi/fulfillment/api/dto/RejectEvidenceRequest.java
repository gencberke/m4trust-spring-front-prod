package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectEvidenceRequest(
    @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion,
    @Min(value = 0, message = "expectedEvidenceVersion must be non-negative")
        long expectedEvidenceVersion,
    @NotBlank(message = "reason is required")
        @Size(min = 1, max = 1000, message = "reason must be 1-1000 characters")
        String reason) {}
