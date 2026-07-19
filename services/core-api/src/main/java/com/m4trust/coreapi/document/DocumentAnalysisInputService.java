package com.m4trust.coreapi.document;
import java.util.Optional; import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
class DocumentAnalysisInputService implements DocumentAnalysisInputPort {
 private final DocumentRepository repository;
 DocumentAnalysisInputService(DocumentRepository repository) { this.repository=repository; }
 public Optional<Input> findAvailable(UUID id) { return repository.findById(id).filter(r -> r.status()==DocumentStatus.AVAILABLE)
  .map(r -> new Input(r.id(),r.dealId(),r.fileName(),r.mediaType(),r.verifiedSizeBytes(),r.verifiedSha256(),r.objectKey(),r.objectVersion())); }
}
