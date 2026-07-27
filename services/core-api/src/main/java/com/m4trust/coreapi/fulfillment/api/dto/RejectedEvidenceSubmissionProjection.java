package com.m4trust.coreapi.fulfillment.api.dto;

import com.m4trust.coreapi.fulfillment.domain.EvidenceMediaType;
import com.m4trust.coreapi.fulfillment.domain.EvidenceSubmissionStatus;
import com.m4trust.coreapi.fulfillment.domain.EvidenceType;
import java.time.Instant;
import java.util.UUID;

public record RejectedEvidenceSubmissionProjection(
    UUID id,
    UUID dealId,
    UUID milestoneId,
    EvidenceType evidenceType,
    EvidenceMediaType mediaType,
    String fileName,
    EvidenceSubmissionStatus status,
    long clientSizeBytes,
    String clientSha256,
    long verifiedSizeBytes,
    String verifiedSha256,
    String objectVersion,
    Instant createdAt,
    Instant submittedAt,
    Instant rejectedAt,
    String rejectionReason,
    EvidenceAvailableActions availableActions,
    long version)
    implements EvidenceSubmissionProjection {}
