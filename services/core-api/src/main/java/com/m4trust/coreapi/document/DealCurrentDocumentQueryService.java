package com.m4trust.coreapi.document;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.deal.DealCurrentDocumentQueryPort;
import com.m4trust.coreapi.deal.DealCurrentDocumentQueryPort.CurrentDealDocument;
import com.m4trust.coreapi.deal.DealCurrentDocumentQueryPort.CurrentDealDocumentActions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document-owned implementation of the Deal-owned currentDocument projection
 * port. The Deal module never imports the document module; this class
 * implements the Deal-owned interface instead, keeping the dependency
 * direction document -&gt; deal only.
 */
@Service
class DealCurrentDocumentQueryService implements DealCurrentDocumentQueryPort {

    private final DocumentRepository repository;

    DealCurrentDocumentQueryService(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentDealDocument> findAvailable(UUID documentId) {
        return repository.findById(documentId)
                .map(Document::rehydrate)
                .filter(document -> document.status() == DocumentStatus.AVAILABLE)
                .map(this::toCurrentDealDocument);
    }

    private CurrentDealDocument toCurrentDealDocument(Document document) {
        // The Deal currentDocument pointer is only ever repointed to an AVAILABLE
        // document during finalize, so it never carries PENDING_UPLOAD mutation
        // authority, and this projection is only reachable through a
        // participant-scoped Deal detail read.
        return new CurrentDealDocument(document.id(), document.dealId(),
                document.fileName(), document.mediaType().value(),
                DocumentStatus.AVAILABLE.name(), document.verifiedSizeBytes(),
                document.verifiedSha256(), document.objectVersion(),
                document.createdAt(), document.availableAt(),
                new CurrentDealDocumentActions(false, true));
    }
}
