package com.m4trust.coreapi.document;

import java.time.Instant;
import java.util.UUID;

record AvailableDealDocument(UUID id, UUID dealId, String fileName,
        String mediaType, DocumentStatus status, long verifiedSizeBytes,
        String verifiedSha256, String objectVersion, Instant createdAt,
        Instant availableAt, DocumentAvailableActions availableActions) {
}
