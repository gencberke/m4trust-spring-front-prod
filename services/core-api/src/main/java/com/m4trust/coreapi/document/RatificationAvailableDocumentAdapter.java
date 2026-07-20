package com.m4trust.coreapi.document;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.ratification.RatificationSourcePorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Document-owned source for an immutable, finalized document snapshot. */
@Service
class RatificationAvailableDocumentAdapter implements RatificationSourcePorts.AvailableDocument {
    private final DocumentRepository documents;

    RatificationAvailableDocumentAdapter(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatificationSourcePorts.Document> find(UUID documentId) {
        return documents.findById(documentId)
                .filter(this::hasAvailableFinalizedMetadata)
                .map(Document::rehydrate)
                .map(this::toSourceDocument);
    }

    private boolean hasAvailableFinalizedMetadata(DocumentRepository.DocumentRecord record) {
        return record.status() == DocumentStatus.AVAILABLE
                && record.verifiedSizeBytes() != null
                && record.verifiedSha256() != null
                && record.objectVersion() != null;
    }

    private RatificationSourcePorts.Document toSourceDocument(Document document) {
        String sha256 = document.verifiedSha256();
        if (!sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("Available document has invalid verified SHA-256");
        }
        return new RatificationSourcePorts.Document(
                document.id(), document.dealId(), document.objectVersion(), sha256);
    }
}
