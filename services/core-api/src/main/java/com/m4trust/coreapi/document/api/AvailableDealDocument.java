package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import java.time.Instant;
import java.util.UUID;

record AvailableDealDocument(
    UUID id,
    UUID dealId,
    String fileName,
    String mediaType,
    DocumentStatus status,
    long verifiedSizeBytes,
    String verifiedSha256,
    String objectVersion,
    Instant createdAt,
    Instant availableAt,
    DocumentAvailableActions availableActions) {}
