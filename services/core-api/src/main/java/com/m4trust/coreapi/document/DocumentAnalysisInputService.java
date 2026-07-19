package com.m4trust.coreapi.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentAnalysisInputService implements DocumentAnalysisInputPort {

    private final DocumentRepository repository;

    DocumentAnalysisInputService(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Input> findAvailable(UUID documentId) {
        return repository.findById(documentId)
                .filter(document -> document.status() == DocumentStatus.AVAILABLE)
                .map(document -> new Input(document.id(), document.dealId(),
                        document.fileName(), document.mediaType(),
                        document.verifiedSizeBytes(), document.verifiedSha256(),
                        document.objectKey(), document.objectVersion()));
    }
}
