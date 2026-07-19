package com.m4trust.coreapi.contractintelligence;

import java.time.Instant;
import java.util.UUID;

/** Narrow document-to-analysis boundary; document code never accesses analysis storage. */
public interface DocumentAnalysisSupersedePort {

    void supersedeForDocument(UUID documentId, UUID tenantId, UUID actorUserId,
            UUID legalEntityId, UUID correlationId, Instant occurredAt);
}
