package com.m4trust.coreapi.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentTest {

    private static final String SHA_256 = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void verifiedMetadataMustMatchDeclarationAndFinalizationPinsObjectVersion() {
        Document document = pendingDocument();

        assertThrows(IllegalArgumentException.class, () -> document.markAvailable(
                13, SHA_256, "version-1", CREATED_AT.plusSeconds(10)));
        document.markAvailable(12, SHA_256, "version-1", CREATED_AT.plusSeconds(10));

        assertEquals(DocumentStatus.AVAILABLE, document.status());
        assertEquals("version-1", document.objectVersion());
    }

    @Test
    void expiredPendingDocumentCannotBecomeAvailableAndAvailableCanOnlySupersede() {
        Document document = pendingDocument();
        assertThrows(IllegalStateException.class, () -> document.markAvailable(
                12, SHA_256, "version-1", CREATED_AT.plusSeconds(300)));

        document.markAvailable(12, SHA_256, "version-1", CREATED_AT.plusSeconds(10));
        document.supersede(CREATED_AT.plusSeconds(20));
        assertThrows(IllegalStateException.class,
                () -> document.supersede(CREATED_AT.plusSeconds(30)));
    }

    private Document pendingDocument() {
        return Document.createPending(UUID.randomUUID(), UUID.randomUUID(),
                "contract.pdf", DocumentMediaType.PDF,
                "documents/" + UUID.randomUUID(), 12, SHA_256,
                CREATED_AT.plusSeconds(300), CREATED_AT);
    }
}
