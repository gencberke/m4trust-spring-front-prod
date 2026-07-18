package com.m4trust.coreapi.document;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A finalized retained history version (AVAILABLE or SUPERSEDED). supersededAt
 * is omitted from the wire payload unless the document was superseded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HistoricalDealDocument(UUID id, UUID dealId, String fileName,
        String mediaType, DocumentStatus status, long verifiedSizeBytes,
        String verifiedSha256, String objectVersion, Instant createdAt,
        Instant availableAt, Instant supersededAt,
        DocumentAvailableActions availableActions) implements DealDocumentHistoryItem {
}
