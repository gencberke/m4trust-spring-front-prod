package com.m4trust.coreapi.fulfillment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceDownloadLink(
    UUID evidenceSubmissionId, String objectVersion, String downloadUrl, Instant expiresAt) {}
