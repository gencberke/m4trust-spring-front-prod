package com.m4trust.coreapi.document;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record FinalizeDocumentUploadRequest(
        @Min(1) long sizeBytes,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String sha256) {

    FinalizeDocumentUploadRequest {
        sha256 = sha256 == null ? null : sha256.toLowerCase(java.util.Locale.ROOT);
    }
}
