package com.m4trust.coreapi.document;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

record DocumentUploadIntent(PendingDealDocument document, URI uploadUrl,
        Map<String, String> uploadHeaders, Instant expiresAt) {
    DocumentUploadIntent {
        uploadHeaders = Map.copyOf(uploadHeaders);
    }
}
