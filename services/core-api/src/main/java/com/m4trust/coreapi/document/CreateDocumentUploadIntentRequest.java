package com.m4trust.coreapi.document;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateDocumentUploadIntentRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotNull @Pattern(regexp = "^(application/pdf|application/vnd\\.openxmlformats-officedocument\\.wordprocessingml\\.document)$") String mediaType,
        @Min(1) long sizeBytes,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String sha256) {

    CreateDocumentUploadIntentRequest {
        fileName = fileName == null ? null : fileName.trim();
        sha256 = sha256 == null ? null : sha256.toLowerCase(java.util.Locale.ROOT);
    }
}
