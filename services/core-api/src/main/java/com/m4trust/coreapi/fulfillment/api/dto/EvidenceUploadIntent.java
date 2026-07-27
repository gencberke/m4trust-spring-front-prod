package com.m4trust.coreapi.fulfillment.api.dto;

import java.time.Instant;
import java.util.Map;

public record EvidenceUploadIntent(
    EvidenceSubmissionProjection evidence,
    String uploadUrl,
    Map<String, String> uploadHeaders,
    Instant expiresAt) {}
