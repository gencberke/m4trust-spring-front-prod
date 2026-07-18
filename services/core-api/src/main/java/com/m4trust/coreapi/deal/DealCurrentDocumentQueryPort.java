package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Deal-owned projection port implemented by the document module. Keeps the
 * Deal detail's currentDocument projection available without the deal module
 * importing the document module (which would create a document&lt;-&gt;deal
 * cycle).
 */
public interface DealCurrentDocumentQueryPort {

    Optional<CurrentDealDocument> findAvailable(UUID documentId);

    record CurrentDealDocument(UUID id, UUID dealId, String fileName, String mediaType,
            String status, long verifiedSizeBytes, String verifiedSha256,
            String objectVersion, Instant createdAt, Instant availableAt,
            CurrentDealDocumentActions availableActions) {
    }

    record CurrentDealDocumentActions(boolean canFinalize, boolean canDownload) {
    }
}
