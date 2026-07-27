package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEvidenceUploadIntentRequest(
    @NotBlank(message = "evidenceType is required")
        @Size(max = 32, message = "evidenceType is too long")
        String evidenceType,
    @NotBlank(message = "mediaType is required") @Size(max = 128, message = "mediaType is too long")
        String mediaType,
    @NotBlank(message = "fileName is required")
        @Size(min = 1, max = 255, message = "fileName must be 1-255 characters")
        String fileName,
    @Min(value = 1, message = "sizeBytes must be positive") long sizeBytes,
    @NotBlank(message = "sha256 is required")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "sha256 must be 64 lowercase hex characters")
        String sha256) {}
