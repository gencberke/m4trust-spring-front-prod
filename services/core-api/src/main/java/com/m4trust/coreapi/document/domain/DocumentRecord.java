package com.m4trust.coreapi.document.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshot used to rehydrate and store the Document aggregate. */
public record DocumentRecord(
    UUID id,
    UUID dealId,
    String fileName,
    String mediaType,
    DocumentStatus status,
    String objectKey,
    long declaredSizeBytes,
    String declaredSha256,
    Instant uploadExpiresAt,
    Long verifiedSizeBytes,
    String verifiedSha256,
    String objectVersion,
    Instant createdAt,
    Instant availableAt,
    Instant supersededAt,
    Instant updatedAt,
    long version) {}
