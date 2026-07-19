package com.m4trust.coreapi.document;

import java.time.Instant;
import java.util.UUID;

/** Contract-intelligence capability required when a document version is replaced. */
public interface DocumentAnalysisSupersedePort {

    void supersedeForDocument(UUID dealId, UUID documentId, UUID tenantId, UUID actorUserId,
            UUID legalEntityId, UUID correlationId, Instant occurredAt);
}
