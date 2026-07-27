package com.m4trust.coreapi.fulfillment.api.dto;

import com.m4trust.coreapi.fulfillment.domain.EvidenceMediaType;
import com.m4trust.coreapi.fulfillment.domain.EvidenceSubmissionStatus;
import com.m4trust.coreapi.fulfillment.domain.EvidenceType;
import java.time.Instant;
import java.util.UUID;

public record PendingEvidenceSubmissionProjection(
    UUID id,
    UUID dealId,
    UUID milestoneId,
    EvidenceType evidenceType,
    EvidenceMediaType mediaType,
    String fileName,
    EvidenceSubmissionStatus status,
    long clientSizeBytes,
    String clientSha256,
    Instant expiresAt,
    Instant cancelledAt,
    Instant createdAt,
    EvidenceAvailableActions availableActions,
    long version)
    implements EvidenceSubmissionProjection {}
