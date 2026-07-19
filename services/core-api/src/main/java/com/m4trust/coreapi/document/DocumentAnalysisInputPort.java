package com.m4trust.coreapi.document;
import java.util.Optional; import java.util.UUID;
public interface DocumentAnalysisInputPort {
 Optional<Input> findAvailable(UUID documentId);
 record Input(UUID id, UUID dealId, String fileName, String mediaType, long sizeBytes,
              String sha256, String objectKey, String objectVersion) { }
}
