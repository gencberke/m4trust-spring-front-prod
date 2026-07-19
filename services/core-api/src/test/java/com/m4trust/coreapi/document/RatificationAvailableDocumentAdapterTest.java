package com.m4trust.coreapi.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RatificationAvailableDocumentAdapterTest {
    private static final Instant CREATED = Instant.parse("2026-07-19T10:00:00Z");

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final RatificationAvailableDocumentAdapter adapter =
            new RatificationAvailableDocumentAdapter(documents);

    @Test
    void returnsOnlyAvailableFinalizedDocumentWithImmutableMetadata() {
        UUID id = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        when(documents.findById(id)).thenReturn(Optional.of(record(id, dealId,
                DocumentStatus.AVAILABLE, 1L, "a".repeat(64), "v1", null)));

        var source = adapter.find(id).orElseThrow();

        assertEquals(id, source.documentId());
        assertEquals(dealId, source.dealId());
        assertEquals("v1", source.objectVersion());
        assertEquals("a".repeat(64), source.sha256());
    }

    @Test
    void returnsEmptyForMissingPendingSupersededOrIncompleteAvailableDocument() {
        UUID id = UUID.randomUUID();
        when(documents.findById(id)).thenReturn(Optional.empty());
        assertFalse(adapter.find(id).isPresent());

        when(documents.findById(id)).thenReturn(Optional.of(record(id, UUID.randomUUID(),
                DocumentStatus.PENDING_UPLOAD, null, null, null, null)));
        assertFalse(adapter.find(id).isPresent());

        when(documents.findById(id)).thenReturn(Optional.of(record(id, UUID.randomUUID(),
                DocumentStatus.SUPERSEDED, 1L, "a".repeat(64), "v1", CREATED.plusSeconds(1))));
        assertFalse(adapter.find(id).isPresent());

        when(documents.findById(id)).thenReturn(Optional.of(record(id, UUID.randomUUID(),
                DocumentStatus.AVAILABLE, null, null, null, null)));
        assertFalse(adapter.find(id).isPresent());
    }

    @Test
    void rejectsAnAvailableDocumentWithNoncanonicalVerifiedDigest() {
        UUID id = UUID.randomUUID();
        when(documents.findById(id)).thenReturn(Optional.of(record(id, UUID.randomUUID(),
                DocumentStatus.AVAILABLE, 1L, "A".repeat(64), "v1", null)));

        assertThrows(IllegalStateException.class, () -> adapter.find(id));
    }

    private static DocumentRepository.DocumentRecord record(
            UUID id,
            UUID dealId,
            DocumentStatus status,
            Long verifiedSize,
            String verifiedSha,
            String objectVersion,
            Instant supersededAt) {
        Instant availableAt = status == DocumentStatus.PENDING_UPLOAD ? null : CREATED.plusSeconds(1);
        return new DocumentRepository.DocumentRecord(id, dealId, "contract.pdf", "application/pdf",
                status, "documents/" + id, 1, "a".repeat(64), CREATED.plusSeconds(3600),
                verifiedSize, verifiedSha, objectVersion, CREATED, availableAt, supersededAt,
                supersededAt == null ? CREATED : supersededAt, 0);
    }
}
