package com.m4trust.coreapi.fulfillment;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Provider-neutral boundary for direct evidence object transfer. */
public interface FulfillmentObjectStorage {

    DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength);

    DirectDownload createDirectDownload(String objectKey, String objectVersion);

    VerifiedObject verify(String objectKey);

    record DirectUpload(URI url, Map<String, String> headers, Instant expiresAt) {
        public DirectUpload {
            headers = Map.copyOf(headers);
        }
    }

    record DirectDownload(URI url, Instant expiresAt) {
    }

    record VerifiedObject(long sizeBytes, String sha256, String objectVersion, String mediaType) {
        public VerifiedObject {
            if (sizeBytes <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || objectVersion == null || objectVersion.isBlank()
                    || mediaType == null || mediaType.isBlank()) {
                throw new IllegalArgumentException("invalid verified object metadata");
            }
        }
    }
}
