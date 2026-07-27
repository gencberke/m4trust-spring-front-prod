package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import java.time.Instant;
import java.util.UUID;

record PendingDealDocument(
    UUID id,
    UUID dealId,
    String fileName,
    String mediaType,
    DocumentStatus status,
    long clientSizeBytes,
    String clientSha256,
    Instant expiresAt,
    Instant createdAt,
    DocumentAvailableActions availableActions)
    implements DealDocumentHistoryItem {}
