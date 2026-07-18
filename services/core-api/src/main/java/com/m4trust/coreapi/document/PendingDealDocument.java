package com.m4trust.coreapi.document;

import java.time.Instant;
import java.util.UUID;

record PendingDealDocument(UUID id, UUID dealId, String fileName,
        String mediaType, DocumentStatus status, long clientSizeBytes,
        String clientSha256, Instant expiresAt, Instant createdAt,
        DocumentAvailableActions availableActions) {
}
