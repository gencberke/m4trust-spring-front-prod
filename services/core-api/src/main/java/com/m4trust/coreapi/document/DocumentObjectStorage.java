package com.m4trust.coreapi.document;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Provider-neutral boundary for direct contractual document object transfer. */
public interface DocumentObjectStorage {

    DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength);

    DirectDownload createDirectDownload(String objectKey, String objectVersion);

    /**
     * Creates the longer-lived immutable download capability carried in an AI
     * command event. Browser links continue to use the regular download TTL.
     */
    default DirectDownload createAiDirectDownload(String objectKey, String objectVersion) {
        return createDirectDownload(objectKey, objectVersion);
    }

    VerifiedObject verify(String objectKey);

    record DirectUpload(URI url, Map<String, String> headers, Instant expiresAt) {
        public DirectUpload {
            headers = Map.copyOf(headers);
        }
    }

    record DirectDownload(URI url, Instant expiresAt) {
    }

    record VerifiedObject(long sizeBytes, String sha256, String objectVersion) {
        public VerifiedObject {
            if (sizeBytes <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || objectVersion == null || objectVersion.isBlank()) {
                throw new IllegalArgumentException("invalid verified object metadata");
            }
        }
    }
}
