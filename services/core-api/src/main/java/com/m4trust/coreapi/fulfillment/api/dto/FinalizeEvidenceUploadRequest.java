package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FinalizeEvidenceUploadRequest(
    @Min(value = 1, message = "sizeBytes must be positive") long sizeBytes,
    @NotBlank(message = "sha256 is required")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "sha256 must be 64 lowercase hex characters")
        String sha256) {}
