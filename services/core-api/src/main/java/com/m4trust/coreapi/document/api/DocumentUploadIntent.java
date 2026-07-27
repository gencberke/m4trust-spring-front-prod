package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

record DocumentUploadIntent(
    PendingDealDocument document,
    URI uploadUrl,
    Map<String, String> uploadHeaders,
    Instant expiresAt) {
  DocumentUploadIntent {
    uploadHeaders = Map.copyOf(uploadHeaders);
  }
}
